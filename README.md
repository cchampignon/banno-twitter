# Twitter Sample Stream Stats
Consuming the Twitter sample stream with akka-htp, typed actors and spray-json written in scala.
## Running
The easiest way to run the app is with sbt. Twitter oauth1 credentials are required as system properties. 
Load sbt with them like so
`sbt -DconsumerKey=<your-consumer-key> -DconsumerSecret=<your-consumer-secret> -Dtoken=<you-token>-DtokenSecret=<your-token-secret>`

### TODO
- Lightweight Oauth. I was unable to find a suitable Oauth1 lib that didn't pull in the kitchen sink. Or if they were
lightweight they didn't support Scala 2.13. Roll my own or fork and publish for 2.13
- Use the binding future that akka-http returns after starting up the http server.
- Proper logging. Replace printlns with logging as well as more logging overall.
- Config. Values such as how many entries in a top listing should be in a reference.conf/application.conf so that the app
doesn't need to be recompiled to adjust those values.
-Back pressure actors. Replace ActorSink.actorRef with ActorSink.actorRefWithBackpressure and amend the protocol to support it.


### Improvements
- Follow URL shorteners. The Twitter API makes mention of having support for this, but more research would be necessary.
This would result in more accurate domain name stats because using shorteners has been common on Twitter.
- Use extended entities. The extended entities was added to support multiple media attachments. The existing
implementation meets the requirement of counting tweets that have a photo. However, adding support for extended entities 
will allow for additional and more accurate stats about photos such as total number of photos.
- Improved dev/run round-trips. Implement CRTL-C (or similar) to stop running app and return to the sbt console gracefully.