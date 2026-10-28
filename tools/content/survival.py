# SPDX-License-Identifier: GPL-3.0-or-later
"""Source text for the bundled Survival Handbook. Build with tools/content/build_guides.py."""
from common import art, CDC_WATER, NDMA, NCDC_HEAT, READY, SPHERE, WHO_GDWQ, WHO_WATER, ICAO_SIGNALS, USDA_FOOD

S = lambda sid, title, style, items: (sid, title, style, items)

SURVIVAL = {
    "id": "survival",
    "title": "Survival Handbook",
    "categories": [
        {"id": "water", "title": "Water and hydration", "articles": [
            art("water-sources", "Finding safer water", "Where to look for water and what to avoid.",
                ["water", "source", "well", "rain", "handpump", "river"], [
                S("best", "Safest first", "steps", [
                    "Sealed bottled or packaged water.",
                    "Tap water, if local authorities say it is safe.",
                    "Water from a protected handpump, tube well or covered well.",
                    "Rainwater collected straight into a clean container.",
                    "Flowing water from a stream or river, upstream of villages and animals.",
                ]),
                S("avoid", "Avoid", "avoid", [
                    "Flood water: it often contains sewage and chemicals.",
                    "Water near toilets, drains, factories, fields sprayed with pesticide, or dead animals.",
                    "Still, stagnant ponds with scum or a strange smell or colour.",
                ]),
                S("treat", "Always", "bullets", [
                    "Treat water from any source except sealed bottles before drinking. See Filtration versus disinfection.",
                    "Use treated water for drinking, cooking, brushing teeth and washing food.",
                ]),
            ], [WHO_WATER, CDC_WATER]),
            art("water-treatment", "Filtration versus disinfection", "Making water safer to drink.",
                ["boil", "boiling", "chlorine", "tablets", "filter", "purify", "sodis", "solar"], [
                S("idea", "The difference", "bullets", [
                    "Filtering through cloth, sand or a ceramic filter removes dirt but does not reliably kill germs.",
                    "Disinfecting by boiling, chlorine tablets or sunlight kills most germs.",
                    "For cloudy water, do both: settle, filter, then disinfect.",
                ]),
                S("steps", "Make water safer", "steps", [
                    "Let cloudy water stand so dirt settles, then pour off the clear water.",
                    "Strain it through a clean, folded cotton cloth.",
                    "Boil it at a full rolling boil for 1 minute. Above about 2,000 m altitude, boil for 3 minutes.",
                    "Cool it covered, and store it in a clean, covered container.",
                ]),
                S("other", "If you cannot boil", "bullets", [
                    "Use water purification tablets or drops, following the packet for dose and waiting time.",
                    "Solar disinfection: fill clear plastic (PET) bottles of 2 litres or less with clear water and leave them in full sun for at least 6 hours, or 2 days if it is cloudy.",
                ]),
                S("limits", "Limits", "warning", [
                    "Boiling and tablets do not remove chemicals such as fuel, pesticides or industrial waste. Find another source.",
                    "Solar disinfection does not work for cloudy water or on days of continuous rain.",
                ]),
            ], [CDC_WATER, WHO_GDWQ]),
            art("water-storage", "Safe water storage", "Keeping treated water clean.",
                ["store", "storage", "container", "how much water"], [
                S("how", "How to store", "steps", [
                    "Use clean containers with a tight lid, ideally with a tap or narrow neck.",
                    "Pour water out instead of dipping cups or hands in.",
                    "Keep containers off the floor, out of sunlight and away from fuel and chemicals.",
                    "Label containers with the date you filled them.",
                ]),
                S("amount", "How much", "bullets", [
                    "Plan at least 3 litres per person per day for drinking and cooking, and more in hot weather.",
                    "Washing and hygiene need much more; store extra when you can.",
                ]),
                S("refresh", "Keeping it fresh", "bullets", [
                    "Replace water you stored yourself about every 6 months.",
                    "Use sealed bottled water before its expiry date.",
                ]),
            ], [SPHERE, READY]),
            art("dehydration-signs", "Dehydration warning signs", "When fluid loss becomes dangerous.",
                ["dehydration", "thirst", "urine", "heat"], [
                S("early", "Early signs", "bullets", ["Thirst and a dry mouth.", "Dark yellow urine or peeing less.", "Tiredness, headache or dizziness."]),
                S("danger", "Danger signs", "warning", [
                    "Confusion, fainting or a racing heartbeat.",
                    "No urine for 8 hours or more.",
                    "Sunken eyes, or a sunken soft spot on a baby's head.",
                ]),
                S("do", "What to do", "bullets", [
                    "Drink small amounts often. Use oral rehydration salts for diarrhoea or vomiting.",
                    "See First Aid › Dehydration for full steps.",
                ]),
            ], [SPHERE]),
        ]},
        {"id": "shelter", "title": "Shelter and weather", "articles": [
            art("emergency-shelter", "Basic emergency shelter", "Staying dry, out of the wind and off cold ground.",
                ["shelter", "tarp", "tent", "camp", "rain"], [
                S("priorities", "Priorities", "steps", [
                    "Get out of rain, wind and direct sun.",
                    "Insulate yourself from the ground with dry leaves, cardboard, mats or spare clothes.",
                    "Use existing safe structures before building one.",
                ]),
                S("site", "Choosing a spot", "avoid", [
                    "Low ground, dry riverbeds and stream banks that can flood suddenly.",
                    "Under dead trees, loose rocks, damaged buildings or power lines.",
                ]),
                S("warning", "Danger", "warning", [
                    "Never use charcoal, stoves, heaters or generators inside a closed shelter or tent. Carbon monoxide can kill without warning.",
                ]),
            ], [NDMA]),
            art("staying-warm", "Staying warm", "Holding on to body heat in cold or wet conditions.",
                ["cold", "warm", "winter", "layers", "hypothermia"], [
                S("do", "Do", "steps", [
                    "Wear several loose layers rather than one thick one.",
                    "Cover your head, neck, hands and feet.",
                    "Change out of wet clothes as soon as you can.",
                    "Eat and drink regularly; warm drinks help.",
                    "Huddle together to share warmth.",
                ]),
                S("avoid", "Avoid", "avoid", [
                    "Alcohol: it makes you lose heat faster.",
                    "Sitting or lying directly on cold ground.",
                    "Burning fuel indoors without good ventilation.",
                ]),
                S("watch", "Watch for", "warning", ["Uncontrolled shivering, confusion or slurred speech. See First Aid › Hypothermia."]),
            ], [NDMA]),
            art("extreme-heat", "Managing extreme heat", "Staying safe during heatwaves.",
                ["heatwave", "heat", "loo", "summer", "sun"], [
                S("do", "Do", "steps", [
                    "Drink water regularly, even if you are not thirsty.",
                    "Stay indoors or in shade during the hottest hours, especially around noon to 3 pm.",
                    "Wear light, loose cotton clothes and cover your head outdoors.",
                    "Use ORS, buttermilk, lassi, lemon water or rice water to replace salts.",
                    "Check on older people, children, and anyone ill or living alone.",
                ]),
                S("avoid", "Avoid", "avoid", [
                    "Heavy work in the afternoon sun.",
                    "Alcohol, and large amounts of tea, coffee or fizzy drinks.",
                    "Leaving children or animals in parked vehicles, even briefly.",
                ]),
                S("watch", "Watch for", "warning", ["Confusion, very hot skin or collapse are signs of heatstroke. Call 112."]),
            ], [NDMA, NCDC_HEAT]),
            art("storms-floods", "Storms and floods", "Before, during and after cyclones, storms and floods.",
                ["flood", "cyclone", "storm", "monsoon", "rain", "lightning"], [
                S("before", "Before", "steps", [
                    "Follow official warnings from the India Meteorological Department and local authorities.",
                    "Charge phones and power banks and keep your emergency kit ready.",
                    "Move important documents and medicines to a high, dry place.",
                    "Know the way to higher ground or the nearest shelter.",
                ]),
                S("during", "During", "steps", [
                    "Move to higher ground if water is rising.",
                    "Stay indoors away from windows during a cyclone. A sudden calm may be the eye of the storm; stay inside.",
                    "Switch off electricity and gas at the mains if told to, or if water is entering and it is safe.",
                ]),
                S("never", "Never", "avoid", [
                    "Walk or drive through flood water. Shallow moving water can knock you over and sweep a car away.",
                    "Touch electric poles, fallen wires or wet electrical equipment.",
                    "Shelter under trees or stand in open fields during lightning.",
                ]),
                S("after", "After", "bullets", [
                    "Return home only when authorities say it is safe.",
                    "Treat all water before drinking and throw away food touched by flood water.",
                ]),
            ], [NDMA]),
            art("earthquake", "Earthquakes", "Drop, cover and hold on.",
                ["earthquake", "tremor", "aftershock", "building collapse"], [
                S("during", "During shaking", "steps", [
                    "Drop to your hands and knees.",
                    "Take cover under a sturdy table or desk, or against an inside wall, and protect your head and neck.",
                    "Hold on until the shaking stops.",
                    "If outdoors, move to an open area away from buildings, trees and power lines.",
                ]),
                S("avoid", "Do not", "avoid", [
                    "Run outside while the ground is shaking.",
                    "Use lifts.",
                    "Stand near windows, shelves or heavy furniture.",
                ]),
                S("after", "After", "bullets", [
                    "Expect aftershocks.",
                    "Check for gas leaks by smell and do not use flames or switches if you suspect one.",
                    "Leave damaged buildings and stay out until they are declared safe.",
                ]),
            ], [NDMA]),
            art("evacuation-prep", "Basic evacuation preparation", "Being ready to leave quickly.",
                ["evacuate", "evacuation", "leave", "go bag", "relief camp"], [
                S("steps", "Get ready", "steps", [
                    "Know at least two ways out of your area and where official shelters are.",
                    "Agree a meeting point and one contact outside the area that everyone will call or message.",
                    "Keep a go-bag ready (see Preparedness › Evacuation checklist).",
                    "Keep fuel in your vehicle above half a tank during warnings, if you have one.",
                ]),
                S("leaving", "When told to leave", "steps", [
                    "Leave early. Roads get blocked as conditions worsen.",
                    "Switch off electricity, gas and water if authorities advise it.",
                    "Lock your home and tell your contact where you are going.",
                    "Follow official routes. Do not take shortcuts through flooded or blocked areas.",
                ]),
            ], [NDMA, READY]),
        ]},
        {"id": "navigation", "title": "Navigation and signalling", "articles": [
            art("compass-use", "Using a compass", "Taking and following a direction.",
                ["compass", "north", "bearing", "direction"], [
                S("steps", "Follow a bearing", "steps", [
                    "Hold the phone flat and level, away from metal, magnets and vehicles.",
                    "Point the top of the phone at your target and read the heading.",
                    "Pick a landmark in that direction, walk to it, then repeat.",
                ]),
                S("notes", "Good to know", "bullets", [
                    "Phone compasses need calibration; move the phone in a figure 8 if readings jump.",
                    "Magnetic north differs slightly from true north. HAVEN can show true north when it has a GPS fix.",
                ]),
            ], []),
            art("reading-maps", "Reading maps", "Matching a map to what you see.",
                ["map", "orient", "landmark", "scale"], [
                S("steps", "Steps", "steps", [
                    "Turn the map so north on the map points north.",
                    "Find two or three features you can see, such as a road, river, railway or hill.",
                    "Locate them on the map to work out where you are.",
                    "Plan a route along clear features such as roads or rivers rather than across open country.",
                    "Check your position regularly against landmarks.",
                ]),
                S("offline", "Offline", "bullets", ["Download map regions in HAVEN before you travel."]),
            ], []),
            art("gps-coordinates", "Finding and recording GPS coordinates", "Knowing and sharing exactly where you are.",
                ["gps", "coordinates", "latitude", "longitude", "location"], [
                S("steps", "Steps", "steps", [
                    "Open Tools › GPS coordinates and wait until the accuracy number is small.",
                    "Write down latitude and longitude together with the time.",
                    "Attach the location to a note in the Field Notebook for places you need to find again.",
                ]),
                S("notes", "Good to know", "bullets", [
                    "GPS works without mobile data, but needs a view of the sky.",
                    "Five decimal places locate you to about a metre.",
                    "Read numbers slowly and repeat them back when giving them over the phone.",
                ]),
            ], []),
            art("morse-sos", "Morse SOS and distress signals", "Signalling for help with light, sound or marks.",
                ["sos", "morse", "signal", "whistle", "mirror", "rescue", "helicopter"], [
                S("sos", "SOS", "bullets", [
                    "SOS is three short, three long, three short: ··· ––– ···, sent without gaps and then repeated.",
                    "Use Signal & Light to flash it with the torch or screen, or sound it.",
                ]),
                S("other", "Other signals", "bullets", [
                    "Three of anything is a recognised distress signal: three whistle blasts, three flashes or three fires in a triangle.",
                    "By day, flash sunlight from a mirror or shiny surface towards aircraft or searchers.",
                    "On the ground, make large shapes in contrasting material. A V means you need help; an X means you need medical help.",
                ]),
                S("tips", "Tips", "bullets", ["Light signals carry far at night. Save battery by signalling when you can see or hear searchers."]),
            ], [ICAO_SIGNALS]),
            art("no-data-navigation", "Navigation without mobile data", "Getting around when there is no signal.",
                ["offline", "no network", "no signal", "navigation"], [
                S("before", "Before you go", "steps", [
                    "Download HAVEN map regions for where you are going.",
                    "Save key places (homes, hospitals, shelters) as notes with locations.",
                    "Write important numbers and addresses on paper too.",
                ]),
                S("during", "On the way", "bullets", [
                    "Use GPS coordinates and the compass to check your direction.",
                    "Follow roads and named landmarks.",
                    "In unfamiliar country, staying put where people expect you is often safer than walking out.",
                ]),
            ], []),
            art("route-prep", "Basic route preparation", "Planning a trip so others can find you.",
                ["route", "trip", "plan", "trek", "travel"], [
                S("steps", "Steps", "checklist", [
                    "Tell someone your route and when you expect to arrive.",
                    "Set a safety check-in in HAVEN.",
                    "Check weather warnings.",
                    "Download offline maps for the whole route.",
                    "Carry water, snacks, a torch and a charged power bank.",
                    "Note fuel, food and medical stops along the way.",
                ]),
            ], []),
        ]},
        {"id": "supplies", "title": "Food and supplies", "articles": [
            art("ration-planning", "Emergency ration planning", "Stocking food that keeps and is easy to prepare.",
                ["ration", "food", "stock", "pantry", "planning"], [
                S("principles", "Principles", "bullets", [
                    "Store foods your household already eats and knows how to cook.",
                    "Keep enough ready-to-eat food for at least the first three days.",
                    "Choose foods that need little water or fuel, such as sattu, poha, roasted chana, peanuts and biscuits.",
                    "Use and replace stock in date order so nothing expires.",
                ]),
                S("tool", "Plan quantities", "bullets", ["Use Guides › Emergency Cookbook › Ration and water planner to work out how much you need."]),
            ], [READY]),
            art("emergency-kit", "Emergency kit checklist", "A bag of essentials ready to grab.",
                ["kit", "go bag", "emergency bag", "supplies"], [
                S("kit", "Pack", "checklist", [
                    "Drinking water", "Ready-to-eat food", "Torch and spare batteries", "Charged power bank and cables",
                    "First-aid kit", "Personal medicines for at least a week", "Copies of ID and important documents in a waterproof pouch",
                    "Some cash in small notes", "Whistle", "Battery or wind-up radio", "Matches or lighter in a waterproof container",
                    "Rain cover and a warm layer", "Soap, sanitiser and sanitary products", "Masks", "Blanket or emergency foil blanket",
                    "Phone numbers written on paper", "Items for babies, older people or pets",
                ]),
            ], [READY, NDMA]),
            art("food-storage", "Food storage safety", "Keeping stored food safe to eat.",
                ["food storage", "pantry", "pests", "expiry"], [
                S("do", "Do", "steps", [
                    "Keep food in sealed containers in a cool, dry place off the floor.",
                    "Store food away from fuel, pesticides and cleaning products.",
                    "Use older stock first and check dates regularly.",
                ]),
                S("discard", "Throw away", "warning", [
                    "Food that has touched flood water, unless it is in an undamaged sealed can.",
                    "Cans that are bulging, leaking, badly dented or rusted.",
                    "Anything that smells, looks or feels wrong. When in doubt, throw it out.",
                ]),
            ], [USDA_FOOD]),
            art("battery-power", "Battery and power management", "Making your phone last.",
                ["battery", "power", "charge", "power bank", "phone"], [
                S("save", "Save battery", "steps", [
                    "Turn on battery saver.",
                    "Lower screen brightness and close apps you do not need.",
                    "Switch to airplane mode when you do not need a signal. GPS can still work.",
                    "Switch the phone on at agreed times to check messages.",
                    "Keep the phone warm in cold weather; cold drains batteries fast.",
                ]),
                S("charge", "Charging", "bullets", [
                    "Keep power banks charged and rotate spare batteries.",
                    "A vehicle charger or small solar panel can help during long outages.",
                ]),
                S("never", "Never", "avoid", ["Run a generator indoors or in a closed garage."]),
            ], [READY]),
            art("journey-supplies", "Preparing supplies before a journey", "What to carry when you travel.",
                ["journey", "travel", "trip", "packing"], [
                S("list", "Carry", "checklist", [
                    "Water", "Snacks", "Offline maps downloaded", "Charged phone and power bank", "Torch",
                    "Personal medicines", "Small first-aid kit", "ID and some cash", "Emergency numbers on paper",
                    "Warm or rain layer", "Route shared with someone",
                ]),
            ], []),
        ]},
        {"id": "safety", "title": "Personal safety", "articles": [
            art("when-lost", "What to do when lost", "Stop, think, observe, plan.",
                ["lost", "stuck", "stranded", "missing"], [
                S("stop", "Steps", "steps", [
                    "Stop. Sit down and stay calm.",
                    "Think about how you got here and who knows your plans.",
                    "Observe: check GPS, landmarks, water, shelter and the time of day.",
                    "Plan: if people know your route, stay where you are and make yourself easy to find.",
                ]),
                S("help", "Get found", "bullets", [
                    "Share your coordinates by SMS if you have any signal; texts get through more often than calls.",
                    "Use bright clothing, the torch or three whistle blasts to signal.",
                    "Find shelter before dark and ration water.",
                ]),
            ], []),
            art("roadside", "Roadside emergencies", "Breakdowns and crashes on the road.",
                ["road", "car", "breakdown", "accident", "highway", "crash"], [
                S("steps", "Steps", "steps", [
                    "Pull as far off the road as you can and turn on hazard lights.",
                    "Get everyone out on the side away from traffic and stand well back, behind a barrier if there is one.",
                    "Place a warning triangle well behind the vehicle if it is safe to do so.",
                    "Call 112 for injuries or danger. On national highways, the highway helpline is 1033.",
                ]),
                S("crash", "At a crash", "avoid", [
                    "Do not move injured people unless there is fire or other immediate danger.",
                    "Do not smoke or use flames near leaking fuel.",
                ]),
            ], []),
            art("night-visibility", "Nighttime visibility", "Being seen and seeing at night.",
                ["night", "dark", "visibility", "torch", "reflective"], [
                S("do", "Do", "bullets", [
                    "Wear light-coloured or reflective clothing.",
                    "Carry a torch, or use the phone light or screen light in HAVEN.",
                    "Walk facing oncoming traffic, well off the road.",
                    "Use well-lit routes where possible.",
                ]),
            ], []),
            art("check-ins", "Safety check-ins", "Letting someone know you are okay.",
                ["check in", "check-in", "safety", "alone", "travel"], [
                S("how", "How to use them", "steps", [
                    "Agree with a trusted person what they should do if you miss a check-in.",
                    "Start a check-in in SOS › Safety check-in before you set off.",
                    "Tap I'm safe when you arrive, or extend it if you are delayed.",
                ]),
                S("note", "Note", "bullets", ["HAVEN alerts you when time runs out and can prepare a message for you to send. It never sends anything on its own."]),
            ], []),
            art("share-location", "Communicating location to trusted contacts", "Making sure people can find you.",
                ["share location", "location", "coordinates", "sms", "contact"], [
                S("steps", "Steps", "steps", [
                    "Open SOS and wait for a location with good accuracy.",
                    "Send it to a contact by SMS. Short texts get through on weak signal.",
                    "Add a landmark, such as a temple, school or shop name.",
                    "Say how accurate the fix is and when it was taken.",
                ]),
                S("note", "Remember", "bullets", ["Opening the messaging app does not send the message. Check it was sent."]),
            ], []),
        ]},
        {"id": "preparedness", "title": "Preparedness", "articles": [
            art("power-outage", "Power-outage checklist", "Getting ready for long power cuts.",
                ["power cut", "outage", "electricity", "blackout"], [
                S("list", "Checklist", "checklist", [
                    "Torches and spare batteries in an easy place", "Power banks charged", "Water stored",
                    "Food that needs no refrigeration", "Fridge and freezer kept closed", "Medicines needing cold storage planned for",
                    "Sensitive appliances unplugged", "Generators only used outdoors, away from windows",
                ]),
            ], [READY]),
            art("evacuation-checklist", "Evacuation checklist", "What to do before you leave home.",
                ["evacuation", "leave home", "go bag"], [
                S("list", "Checklist", "checklist", [
                    "Go-bag packed", "Documents and medicines packed", "Phones and power banks charged",
                    "Meeting point and outside contact agreed", "Electricity, gas and water switched off if advised",
                    "Pets and their food ready", "Home locked", "Route and destination shared with your contact",
                ]),
            ], [NDMA, READY]),
            art("household-plan", "Household emergency plan", "Agreeing what everyone will do.",
                ["family plan", "household", "plan", "meeting point"], [
                S("list", "Plan", "checklist", [
                    "Emergency contacts saved in HAVEN and written on paper", "A meeting point near home and one outside the area",
                    "One contact outside the area everyone will message", "Who collects children from school",
                    "Who helps older or disabled family members", "Where the mains switches and gas valve are",
                    "Where the emergency kit is kept", "When the plan was last practised",
                ]),
            ], [READY, NDMA]),
            art("travel-checklist", "Travel and outdoor preparedness checklist", "Before trekking, pilgrimages or long trips.",
                ["travel", "trek", "outdoor", "pilgrimage", "yatra"], [
                S("list", "Checklist", "checklist", [
                    "Weather and route warnings checked", "Offline maps downloaded", "Safety check-in set",
                    "Route shared with a contact", "Water, food and a torch packed", "Personal medicines packed",
                    "Warm and rain layers packed", "Phone and power bank charged", "Emergency numbers on paper",
                ]),
            ], [READY]),
        ]},
    ],
}
