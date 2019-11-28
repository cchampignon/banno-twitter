package com.github.cchampignon

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.twitter.hbc.httpclient.auth.OAuth1
import org.apache.http.client.methods.HttpGet

import scala.sys.SystemProperties

object Oauth {

  private val oauthParams = {
    val properties = new SystemProperties
    for {
      cKey <- properties.get("consumerKey")
      cSec <- properties.get("consumerSecret")
      token <- properties.get("token")
      tSec <- properties.get("tokenSecret")
    } yield new OAuth1(cKey, cSec, token, tSec)
  }

  def withOauthHeader(request: HttpRequest): Option[HttpRequest] = {
    oauthParams.map { oa =>
      val get = new HttpGet(request.uri.toString())
      oa.signRequest(get, null)
      val headers = get.getAllHeaders.toSeq.map(h => RawHeader(h.getName, h.getValue))
      request.withHeaders(headers)
    }
  }
}
