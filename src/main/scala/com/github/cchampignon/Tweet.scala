package com.github.cchampignon

case class Tweet(text: String, entities: Entities)

case class Entities(hashtags: List[Hashtag], urls: List[Url], media: Option[List[Media]])

case class Hashtag(text: String)

case class Url(expanded_url: String)

case class Media(url: String)

