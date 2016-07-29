/**
 *  Slack log
 *
 *  Copyright 2016 Joe
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Motion and Lights Presence",
    namespace: "jhansche",
    author: "Joe",
    description: "Determine presence via motion and lights",
    category: "Fun & Social",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {
}


preferences {
	section("Determine presence with:") {
		input "motion_sensor", "capability.motionSensor", required: true, title: "Which motion sensor?"
        input "light_sensor", "capability.illuminanceMeasurement", required: true, title: "Which light sensor?"
        input "light_threshold", "number", defaultValue: 130, range: "0..1000", required: false, title: "Luminance threshold (in lux)"
        input "light_delta_threshold", "number", defaultValue: 30, range: "0..1000", required: false, title: "Change in luminance (in lux) to trigger a change"
	}
    section("Simulate presence:") {
    	input "presence", "device.simulatedPresenceSensor", require: false, title: "Presence Sensor"
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	state.motion = null
    state.lights = null
    state.light_threshold = 130
    state.light_delta = 30
    state.last_occupied_time = 0
    state.last_unoccupied_time = 0

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    subscribe(motion_sensor, "motion", motionHandler)
    subscribe(light_sensor, "illuminance", lightChangedHandler)

	state.motion = motion_sensor.currentValue("motion")
    state.lights = light_sensor.currentValue("illuminance")
    state.last_state = 'unoccupied'

	if (light_threshold != null) state.light_threshold = light_threshold + 0
    if (light_delta != null) state.light_delta = light_delta + 0

	if (!state.motion) state.motion = 'inactive'
    if (!state.lights) state.lights = 0
    
    log.debug("Current motion=${state.motion}")
    log.debug("Current lights=${state.lights} (threshold=${state.light_threshold}, delta=${state.light_delta})")
}

def motionHandler(evt) {
	log.debug("motionHandler: ${String.valueOf(evt.value)}; last state=" + String.valueOf(state.motion))

	if (state.motion != evt.value) {
	    doReport(evt.value, state.lights)
        state.motion = evt.value
    } else {
    	log.debug("motionHandler: state did not change...")
    }
}

def lightChangedHandler(evt) {
	log.debug("lightChangedHandler: ${String.valueOf(evt.value)}; last state=" + String.valueOf(state.lights))
    int lux = evt.integerValue
	log.debug("event int: ${lux}")
    doReport(state.motion, lux)
    state.lights = lux
}

def doReport(motion, lights) {
	int lightChange = lights - state.lights

    boolean motionChanged = state.motion != motion

	// Whether the lux difference is enough to trigger an update
    boolean lightsChanged = lightChange > state.light_delta || lightChange < -(state.light_delta)
    // Whether the lux value is enough to consider the lights "on" or off
    boolean lightState = lights >= state.light_threshold
    // whether the current on/off state is different from previous state
    boolean lightStateChanged = lightState != state.light_lastState

	String detectedState = 'unknown'

	if (lightState && motion == 'active') {
    	detectedState = 'occupied'
    } else if (!lightState && motion == 'inactive') {
    	detectedState = 'unoccupied'
    }

	boolean didStateChange = state.last_state != detectedState
    long now = new Date().getTime()

    long occupiedSince = 0
    long occupiedDuration = 0

	state.last_state = detectedState

	if (didStateChange) {
    	if (detectedState == 'occupied') {
        	state.last_occupied_time = now

			if (presence != null) {
            	presence.arrived()
            }
        } else if (detectedState == 'unoccupied') {
        	if (state.last_occupied_time > state.last_unoccupied_time) {
            	occupiedSince = state.last_occupied_time
                occupiedDuration = now - occupiedSince
                log.debug("occupied since ${occupiedSince}, after ${occupiedDuration} ms")
            }

			state.last_unoccupied_time = now

            if (presence != null) {
            	presence.departed()
            }
        }
    }
}