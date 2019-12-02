package com.github.cchampignon

import com.github.cchampignon.actors.CountActor.Averages
import com.github.cchampignon.actors.StatActor.{Percentage, Top}
import com.vdurmont.emoji.Emoji
import io.lemonlabs.uri.DomainName

case class AllData(
                    total: Int,
                    averages: Averages,
                    topEmojis: Top[Emoji],
                    percentEmoji: Percentage[Emoji],
                    topHashtags: Top[Hashtag],
                    percentUrl: Percentage[Url],
                    percentPhoto: Percentage[Media],
                    topHost: Top[DomainName],
)
