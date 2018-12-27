/**
 *  Pushover Notification
 *
 *  Author: Digital Flodur 
 *  Date: 2018-12-26
 */

// Automatically generated. Make future change here.
definition(
    name: "Pushover Notification",
    namespace: "Digital Flodur",
    author: "Kurtis@DigitalFlodur.com",
    description: "Pushover service for SmartThing Events, in conjunction with the Pushover-Manager by Tonesto7",
    category: "Convenience",
    iconUrl: "https://drupal.org/files/project-images/pushover-app-logo.png",
    iconX2Url: "https://drupal.org/files/project-images/pushover-app-logo.png",
    oauth: true
)

import groovy.json.JsonSlurper

preferences {
	section("Choose one or more, when..."){
		input "motion", "capability.motionSensor", title: "Motion Here", required: false, multiple: true
		input "contact", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
		input "acceleration", "capability.accelerationSensor", title: "Acceleration Detected", required: false, multiple: true
		input "mySwitch", "capability.switch", title: "Switch Turned On", required: false, multiple: true
		input "arrivalPresence", "capability.presenceSensor", title: "Arrival Of", required: false, multiple: true
		input "departurePresence", "capability.presenceSensor", title: "Departure Of", required: false, multiple: true
        input "lock","capability.lock", title: "Unlock/Lock", required: false, multiple: true
	}
	section("Message Text"){
		input "messageText", "text", title: "Message Text", required: true
	}
    section("Lock Notifications"){
    	input "lockNotify", "enum", title: "Notify on Lock?", required: false,
        metadata :[
        	values: ['Yes', 'No'
            ]
        ]
        input "unlockNotify", "enum", title: "Notify on Unlock?", required: false,
        metadata :[
        	values: ['Yes', 'No'
            ]
        ]
    }
    section("Send SmartThings Notification (optional)"){
    	input "pushNotification", "enum", title: "Send SmartThings Notification?", required: true,
        metadata :[
        	values: ['Yes', 'No'
            ]
        ]
    }
	section("Send SMS Message to (optional)"){
		input "phone", "phone", title: "Phone Number", required: false
	}
    section("Send Pushover Notification (optional)"){
        input "deviceName", "text", title: "Pushover Device Name", required: false
	input "imagelink", "text", title: "Image Link", required: false
	input "imagelink_title", "text", title: "Image Link Title", required: false
        input "priority", "enum", title: "Pushover Priority", required: false,
        metadata :[
           values: [ 'Badge Only', 'Low', 'Normal', 'High', 'Emergency'
           ]
        ]
        input "sound", "enum", title: "Pushover Alert Sound", required: false,
        metadata :[
           values: [ 'pushover', 'bike', 'bugle', 'cashregister', 'classical', 'cosmic',
           'falling', 'gamelan', 'incoming', 'intermission', 'magic', 'mechanical', 'pianobar',
           'siren', 'spacealarm', 'tugboat', 'alien', 'climb', 'persistent', 'echo', 'updown', 'none'
           ]
        ]
    }

}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribeToEvents()
    
    // Custom states
    state.priorityMap = ["Low":-1,"Normal":0,"High":1,"Emergency":2];
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribeToEvents()
    
    // Custom states
    state.priorityMap = ["Badge Only":-2,"Low":-1,"Normal":0,"High":1,"Emergency":2];
}

def subscribeToEvents() {
	subscribe(contact, "contact.open", sendMessage)
	subscribe(acceleration, "acceleration.active", sendMessage)
	subscribe(motion, "motion.active", sendMessage)
	subscribe(mySwitch, "switch.on", sendMessage)
	subscribe(arrivalPresence, "presence.present", sendMessage)
	subscribe(departurePresence, "presence.not present", sendMessage)
    subscribe(lock, "lock", sendMessage)
}

def sendMessage(evt) {
	log.debug "$evt.name: $evt.value, $messageText"
    
    def thisMessageText = messageText;
    
    if(evt.name == "lock")
    {
      if ((evt.value == "unlocked") && (unlockNotify == "Yes"))
      {
        log.debug "$evt.data"
        
        if(evt.data)
        {
          def data = new JsonSlurper().parseText(evt.data)
          log.debug data.usedCode
          thisMessageText = messageText + ", Used Code[$data.usedCode]";
        }
      }
      else if ((evt.value == "locked") && (lockNotify == "Yes"))
      {
        log.debug "$evt.data"
      }
      else
      {
        return;
      }
    }
    
    if(pushNotification == "Yes")
    {
      log.debug "Sending SmartThings Notification"
	  sendPush(thisMessageText)
	}
    else
    {
      log.debug "Skipping SmartThings Notification"
    }
    
    if (phone)
    {
      log.debug "Sending SMS message to [$phone]"
	  sendSms(phone, thisMessageText)
	}
    else
    {
      log.debug "Skipping SMS message"
    }
    
}

def sendMessagePage() {
    return dynamicPage(name: "sendMessagePage", title: "Notification Test", install: false, uninstall: false) {
        section() {
            if(state?.testMessageSent == true) {
                paragraph title: "Oops", "Message Already Sent...\nGo Back to MainPage to Send again..."
            } else {
                paragraph title: "Sending Message: ", "${settings?.testMessage}", state: "complete"
                paragraph "Device(s): ${settings?.pushoverDevices}" 
                Map msgObj = [
                    title: app?.getLabel(), //Optional and can be what ever
                    html: false, //Optional see: https://pushover.net/api#html
                    message: "$messageText", //Required (HTML markup requires html: true, parameter)
                    priority: 0,  //Optional
                    retry: 30, //Requried only when sending with High-Priority
                    expire: 10800, //Requried only when sending with High-Priority
                    sound: "$sound" //Optional
	       		url: "$imagelink", //Optional
			url_title: "$imagelink_title" //Optional
		]
                /* buildPushMessage(List param1, Map param2, Boolean param3)
                    Param1: List of pushover Device Names
                    Param2: Map msgObj above
                    Param3: Boolean add timeStamp
                */
                buildPushMessage(settings?.pushoverDevices, msgObj, true) // This method is part of the required code block
            }
            state?.testMessageSent = true
        }
    }
}

//PushOver-Manager Input Generation Functions
private getPushoverSounds(){return (Map) atomicState?.pushoverManager?.sounds?:[:]}
private getPushoverDevices(){List opts=[];Map pmd=atomicState?.pushoverManager?:[:];pmd?.apps?.each{k,v->if(v&&v?.devices&&v?.appId){Map dm=[:];v?.devices?.sort{}?.each{i->dm["${i}_${v?.appId}"]=i};addInputGrp(opts,v?.appName,dm);}};return opts;}
private inputOptGrp(List groups,String title){def group=[values:[],order:groups?.size()];group?.title=title?:"";groups<<group;return groups;}
private addInputValues(List groups,String key,String value){def lg=groups[-1];lg["values"]<<[key:key,value:value,order:lg["values"]?.size()];return groups;}
private listToMap(List original){original.inject([:]){r,v->r[v]=v;return r;}}
private addInputGrp(List groups,String title,values){if(values instanceof List){values=listToMap(values)};values.inject(inputOptGrp(groups,title)){r,k,v->return addInputValues(r,k,v)};return groups;}
private addInputGrp(values){addInputGrp([],null,values)}
//PushOver-Manager Location Event Subscription Events, Polling, and Handlers
public pushover_init(){subscribe(location,"pushoverManager",pushover_handler);pushover_poll()}
public pushover_cleanup(){state?.remove("pushoverManager");unsubscribe("pushoverManager");}
public pushover_poll(){sendLocationEvent(name:"pushoverManagerCmd",value:"poll",data:[empty:true],isStateChange:true,descriptionText:"Sending Poll Event to Pushover-Manager")}
public pushover_msg(List devs,Map data){if(devs&&data){sendLocationEvent(name:"pushoverManagerMsg",value:"sendMsg",data:data,isStateChange:true,descriptionText:"Sending Message to Pushover Devices: ${devs}");}}
public pushover_handler(evt){Map pmd=atomicState?.pushoverManager?:[:];switch(evt?.value){case"refresh":def ed = evt?.jsonData;String id = ed?.appId;Map pA = pmd?.apps?.size() ? pmd?.apps : [:];if(id){pA[id]=pA?."${id}"instanceof Map?pA[id]:[:];pA[id]?.devices=ed?.devices?:[];pA[id]?.appName=ed?.appName;pA[id]?.appId=id;pmd?.apps = pA;};pmd?.sounds=ed?.sounds;break;case "reset":pmd=[:];break;};atomicState?.pushoverManager=pmd;}
//Builds Map Message object to send to Pushover Manager
private buildPushMessage(List devices,Map msgData,timeStamp=false){if(!devices||!msgData){return};Map data=[:];data?.appId=app?.getId();data.devices=devices;data?.msgData=msgData;if(timeStamp){data?.msgData?.timeStamp=new Date().getTime()};pushover_msg(devices,data);}
