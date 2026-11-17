# Changelog

<!--
Telegram: end each version's section with a hidden block for the StillCraft chat. Start it with a
line "<!-- tg", then 2-4 short lines in Russian for users (what is new, what is fixed), no markdown,
then close it like any HTML comment. The release notes carry it, and the chat bot posts those lines.
-->

## [1.4.0]

- Health Connect is read by itself when the app opens (at most every 15 minutes), so steps and patrols no longer wait for “Fetch now”.
- Optional background reading, about hourly, while the app is closed: Settings → Health Connect. It asks for Health Connect's background permission only when turned on.
- The weather of a run is now asked for its start rounded to about a kilometre, so the exact spot never leaves the phone.
- A privacy policy (PRIVACY.md), linked from the "What Ranger reads" screen in Health Connect, and a release bundle for Google Play.

<!-- tg
«Следопыт» 1.4.0.
Шаги и дозоры теперь подтягиваются из Health Connect сами при открытии приложения.
В настройках можно включить чтение в фоне, примерно раз в час.
-->

## [1.3.0]

- Every creature now has a painted portrait instead of a seal: a watercolour from the ranger's bestiary, in colour once met, a faded sepia sketch while you're on its trail (with a gold arc for how far along), and only a pencil ghost while unseen.
- The bestiary shows three larger portraits to a row, a count per tier, and filters: all, met, on the trail, unseen.
- A creature's page opens on a large portrait with the hunt's three steps (tracks, lair, meeting) marked off.
- The chronicle: proper round buttons for the months, a way back to the current month, the month's totals as numbers, today circled on the calendar.
- The outpost is painted: a watercolour for every rank, the camp growing in place from a campfire to a stone keep with a golden flag, by night or by day as the real sky is, with the campfire's light breathing over it.
- Feat medals are painted too, struck in the metal of their shelf, and the rank crests are painted shields that grow grander with each rank.
- Hand-inked icons for the tabs and the header, and a painted app icon.
- Painted banners at the head of the bestiary, the hall of feats, the chronicle, the summary, settings and every sheet.
- Share cards: a patrol, a creature, a feat, a month, any period of the summary and the ranger's own record become a picture card (post or story shape) to send to any app.
- A calmer summary: each part is its own card with an icon (totals, pace and pulse, zones, form, steps, resting pulse, records, years), the totals are tiles with arrows against the period before, and charts carry their dates and latest value.
- The chronicle keeps its switch between months and summary pinned at the top, and the summary adds a bar that jumps straight to any part.
- "Support the author" in settings.

<!-- tg
«Следопыт» 1.3.0.
Каждое существо бестиария теперь нарисовано акварелью вместо печати. Пока вы идёте по следу, портрет проявляется сепией.
Застава, медали подвигов, гербы званий, иконки и баннеры тоже перерисованы. Застава растёт со званием и меняется днём и ночью.
Появились открытки: дозором, существом, подвигом, месяцем или своей летописью можно поделиться картинкой.
-->

## [1.2.0]

- Feats: 56 one-off deeds across distances, the road behind, rhythm, sky and hour, people and places, and the bestiary. Each is a medal; unearned ones show how far along they are, and the closest ones wait on the outpost.
- The bestiary grows from 36 to 57 creatures: dwellers that need two things at once (the grey heron, the night bat, the ermine…), new rare visitors and four new legends.
- A tidier bestiary: four seals to a row, progress under each seal, names that no longer break mid-word.
- An empty map now says when Health Connect routes aren't allowed, and one button allows them and fetches the tracks.
- A patrol's numbers sit in even columns; giving up a campaign asks first; the last patrol says "2 days ago".

<!-- tg
«Следопыт» 1.2.0.
Подвиги: 56 медалей за дистанции, погоду, время суток, ритм, компанию и бестиарий. У неполученных видно, сколько осталось.
Бестиарий вырос с 36 до 57 существ: цапля, ночница, горностай, волчья стая, речной змей, грозовой дракон и другие.
Если карта пустая из-за доступа к маршрутам Health Connect, приложение подскажет и подтянет треки одной кнопкой.
-->

## [1.1.0]

- Health Connect: choose what to read (runs, resting pulse and HRV, steps). The first read starts today; after that, whatever appeared since last time.
- Walks that a watch filed as runs (slower than 12 min/km with a calm pulse) are no longer patrols; their steps still count as walking.
- Summary over 7 days, 30 days, a year or all time: distance, time, climb, average pace and pulse, the longest run, a comparison with the span before, and pace and pulse trends.
- The rank ladder: every rank with the valour it needs, the day it was reached and what it builds at the outpost.

<!-- tg
Обновление «Следопыта» 1.1.0.
Health Connect: можно выбрать, что тянуть, — пробежки, пульс покоя и HRV, шаги. Прогулки, которые часы записали как бег, больше не попадают в дозоры.
В сводке появилась статистика за 7 дней, 30 дней, год и всё время: темп, пульс, километры и их динамика.
На экране звания видна вся лестница: какие получены и когда, что впереди и что каждое строит на заставе.
-->

## [1.0.0]

The first version.

- Runs from Health Connect (route, heart rate, distance, climb, resting pulse, HRV, daily steps), GPX, TCX and FIT files, whole Strava archives, GPS recording or by hand; one copy of each run is kept.
- 36 creatures found by weather, sun, moon, terrain along the way and heart-rate zones: tracks, lair, meeting.
- Valour from training load, ten ranks that build the outpost, nine abilities, condition from fitness and fatigue with the healer's advice.
- A parchment map of the land your trails have scouted, with home and work hidden.
- Campaigns, records, the chronicle and summary, boots, the band, and the music that played.
- Steps from Health Connect or typed in by hand, with a daily goal.

<!-- tg
Новое приложение «Следопыт»: беговой дневник, где каждая пробежка — дозор на краю королевства.
Пробежки приходят из Health Connect, с часов (GPX, TCX, FIT) или из архива Strava.
Кого встретите, решают погода, время суток, луна, вода и холмы вдоль пути и ваш пульс. Всего 36 существ.
Карта открывает только разведанную землю, а застава растёт вместе со званием.
-->
