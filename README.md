# Camera Samples HTTP
Simple Camera sampler via http.
Launch the app on a phone, don't forget to allow the required authorisations, and send HTTP/1.1 requests to the IP located on the top of the screen

Usages : 
- GET /shot.jpg : get a picture from the phone
- GET /quality : get the current picture quality (High/Low)
- POST /quality?value=<quality_value> : set the quality of the pictures :
	- High : the phone will try to auto-focus the phone, can tke a bit longer
	- Low : the phone will take the picture as soon as possible and will send it, may be unclear
