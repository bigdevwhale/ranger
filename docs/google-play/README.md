# Google Play — всё для ручной публикации

Тексты лежат в `listing/<язык>/*.txt` (копируйте как есть), графика — в `graphics/`, пост о выходе — в `post/`.
Ниже — что вписать в каждое поле Play Console и в каком порядке.

## 0. Перед стартом

- **Нужен AAB, не APK.** Релизный workflow теперь кладёт в GitHub Release ещё и `Ranger-v<версия>.aab`.
  В v1.3.0 его ещё нет: первая версия для Play — **следующий релиз** (1.4.0). Секреты `SIGNING_*` в
  репозитории заданы; без них сборка подписывается debug-ключом, а такой файл Play отклонит.
- **Языки.** AAB собирается без разбиения по языкам (`bundle.language.enableSplit = false`), иначе Play
  ставит только язык телефона и переключение языка в настройках перестаёт работать. Не удаляйте этот блок.
- **Регистрация имени пакета.** Play Console при создании приложения пишет «You can't use this package
  name because it hasn't been registered». Порядок
  ([справка Google](https://support.google.com/googleplay/android-developer/answer/16761053)):
  1. Play Console → **Android developer verification** → **Package names** → **Register package name** → `app.ranger`.
  2. **Select key** → ключ с SHA-256 `b65777dede3a375443d42a3c9debb234b506edd13b25f0916a150f9d5f85fb8a`
     (сертификат CN=bigdevwhale, O=StillCraft — им подписаны все релизы, проверено по `Ranger.apk` из v1.3.0).
  3. **Upload APK** → скопировать сниппет и запустить workflow **Package registration proof**
     (Actions → Run workflow → вставить сниппет). Он соберёт релизный APK с файлом
     `assets/adi-registration.properties`, подпишет тем же ключом и проверит отпечаток; APK — в артефактах запуска.
  4. Дождаться письма о регистрации, потом создавать приложение.
- **Подпись (Play App Signing).** При первой загрузке выберите **«Использовать свой ключ»** и загрузите ключ
  из того же keystore через PEPK (инструкция в самом диалоге). Тогда APK из GitHub и версия из Play
  обновляют друг друга. Если оставить «Google создаст ключ подписи», они друг на друга не встанут.
- **versionCode** берётся из номера запуска workflow (`github.run_number`), он только растёт — Play это устраивает.
- **Новый личный аккаунт разработчика** (создан после ноября 2023): перед выходом в production нужен
  закрытый тест — минимум 12 тестировщиков, которые не выходят из теста 14 дней подряд.
- **Политика конфиденциальности** — `PRIVACY.md` в корне (на двух языках, с разделами про Health Connect,
  запись по GPS и журнал музыки). После push ссылка:
  `https://github.com/bigdevwhale/ranger/blob/main/PRIVACY.md`
  (её же открывает экран «What Ranger reads», который Health Connect показывает со своего экрана разрешений).
- **Видео для декларации foreground service** (раздел 4). Снимите заранее 20–40 секунд записи экрана:
  «+» → «Записать дозор» → старт → уведомление в шторке → выключить и включить экран (запись идёт, трек
  растёт) → «Стоп» — уведомление исчезло, дозор сохранён. Загрузите на YouTube (доступ по ссылке).
  Готовое видео — `video/foreground-location.mp4` (1:29, снято на Mi 9 в демо-сборке, система на английском):
  «+» → «Record a patrol» → таймер идёт → уведомление «Ranger · On patrol» в шторке → домой → экран
  выключен 15 с → включён, уведомление на месте → по нему обратно в приложение, таймер ушёл вперёд →
  «Back to the outpost» → уведомление исчезло. Снято в помещении, поэтому GPS пишет «Looking for
  satellites» и дистанция 0: для декларации важно, что служба запускается по нажатию, видна в шторке,
  работает с выключенным экраном и останавливается кнопкой. Если Google попросит показать сам трек,
  переснимите на улице.
- **Автозагрузка из CI** (`.github/workflows/play.yml`) — после каждого успешного Release берёт AAB из
  релиза и отправляет в Play, release notes — из `listing/*/release_notes.txt` на теге. До первой ручной
  загрузки она не нужна, поэтому:
  1. В репозитории Settings → Environments → создать **`google-play`** с Required reviewer (вы) —
     иначе job запустится без подтверждения сразу после релиза.
  2. Play Console → Users and permissions → пригласить `play-publisher@intense-context-514611-h3.iam.gserviceaccount.com`
     с правом выпускать релизы для Ranger.
  3. В Google Cloud у пула `github` (Workload Identity Federation) разрешить репозиторию
     `bigdevwhale/ranger` действовать от имени этого сервисного аккаунта — как для остальных приложений.
  Пока это не сделано, отклоняйте запуск «Google Play» после релиза (или он упадёт на авторизации — без вреда).
  По умолчанию workflow выкладывает в **production**; пока идёт закрытый тест, запускайте его вручную
  с треком `alpha` (closed testing).

## 1. Создание приложения

| Поле | Значение |
|---|---|
| App name | `Ranger: Fantasy Run Journal` |
| Default language | English (United States) – en-US |
| App or game | **App** |
| Free or paid | Free |
| Declarations | обе галочки (Developer Program Policies, US export laws) |

## 2. Store listing → Main store listing

| Поле | en-US | ru-RU (Add translations → Russian) |
|---|---|---|
| App name (≤30) | `listing/en-US/title.txt` | `listing/ru-RU/title.txt` |
| Short description (≤80) | `listing/en-US/short_description.txt` | `listing/ru-RU/short_description.txt` |
| Full description (≤4000) | `listing/en-US/full_description.txt` | `listing/ru-RU/full_description.txt` |
| App icon 512×512 | `graphics/icon-512.png` | та же |
| Feature graphic 1024×500 | `graphics/feature-graphic-en.png` | `graphics/feature-graphic-ru.png` |
| Phone screenshots (2–8) | `graphics/screenshots-en/1…8.png` | `graphics/screenshots-ru/1…8.png` |
| Tablet screenshots | можно не загружать (только телефон, портрет) | — |
| Video | пусто | — |

Скриншоты 1080×1920 собирает `tools/play.py` из скриншотов README (`docs/screenshots/<язык>/*.jpg`,
1080×2341 — экраны демо, нарисованные Robolectric): Play не берёт картинки длиннее двух ширин, поэтому
экран уходит за нижний край под подписью. Подписи — Cormorant, шрифт заголовков приложения, на пергаменте.
На скриншотах: застава, бестиарий, Жар-птица, дозор, карта, источники пробежек, сводка, открытка.
Баннер и пост — нарисованная застава высшего звания ночью (`assets/outpost/night9.webp`). Как пересобрать:

```bash
./gradlew testDebugUnitTest -Prender --tests '*RenderScreens*' --rerun   # экраны демо, en и ru
python tools/shots.py   # docs/screenshots
python tools/play.py    # графика для Play (нужен pip install pillow)
```

## 3. Store settings

| Поле | Значение |
|---|---|
| App category | App → **Health & Fitness** |
| Tags (до 5) | **Running**, **Activity tracker**, **Workout**, **Health & fitness**, **Sports** |
| Email address | *ваш публичный email* — обязательно, его увидят все |
| Website | `https://bigdevwhale.github.io/stillcraft/apps/ranger/` |
| Phone | пусто |
| External marketing | можно оставить включённым |

**Категория.** Это беговой журнал: его ищут рядом со Strava и трекерами пробежек, а Health Connect
с маршрутами и пульсом Play ждёт именно у Health & Fitness. Существа и звания — оформление, а не игра:
проиграть нельзя, всё строится из настоящих пробежек. Если оформить как игру, декларации Health Connect
будут выглядеть странно для ревьюера.

**Теги.** Running — главное: это журнал пробежек (в Play тег относится к Sports, но его можно ставить
и приложению из Health & Fitness, и по нему ищут именно бегуны). Activity tracker — пробежки, шаги,
пульс из Health Connect. Workout — тренировки, нагрузка и состояние. Health & fitness — общий тег
категории. Sports — тот же бег с другой стороны, рядом со спортивными приложениями. Не брать Entertainment
и Virtual reality: существа и застава — оформление журнала, а не отдельная игра. Sports coaching — тоже
нет: планов тренировок и советов тренера в приложении нет, только подсказка знахарки про отдых.

**Имя разработчика** (Settings → Developer account) — **Stillcraft**, как у остальных приложений.

## 4. App content (Policy → App content)

**Privacy policy** — URL выше.

**App access** — *All functionality is available without any access restrictions.*
(Аккаунтов нет. Без Health Connect ревьюер может добавить пробежку вручную или файлом GPX и увидеть
дозор, бестиарий, карту и летопись.)

**Ads** — *No, my app does not contain ads.*

**Content rating** (анкета IARC, категория **All Other App Types**)
- На все вопросы — **No**: насилия нет (существ встречают, а не побеждают), страха, сексуального
  контента, ненормативной лексики, алкоголя, табака и наркотиков нет.
- **Users interact / share content** — **No**: общения между пользователями нет; открытки уходят
  через системное меню «Поделиться» в чужие приложения.
- **Shares location with other users** — **No**.
- **Digital purchases** — No. **Web browser / unrestricted internet** — No.
- Ожидаемый результат: Everyone / PEGI 3 / 0+.

**Target audience and content**
- Target age: **13–15, 16–17, 18 and over**. Младше 13 не отмечать: иначе действует политика Families
  с отдельными требованиями (а у нас данные о здоровье и GPS).
- *Could your store listing unintentionally appeal to children?* → **No**: дневник тренировок для бегунов.

**News apps** — No. **COVID-19** — My app is not a publicly available COVID-19 app.
**Government apps** — No. **Financial features** — *My app doesn't provide any financial features.*

**Health apps** — отметьте **Activity and fitness** и **Health Connect**; медицинские функции,
исследования и прочее — не отмечать.

**Health Connect permissions** (App content → Health Connect, появится после загрузки AAB).
Для каждого разрешения — категория *Activity and fitness* (пульс — *Vitals*, если Play попросит) и обоснование:

| Разрешение | Обоснование |
|---|---|
| `READ_EXERCISE` | Runs and walks recorded by the user's watch or fitness app become entries ("patrols") of the running journal: time, type and duration. |
| `READ_EXERCISE_ROUTES` | The route of each run is drawn on the in-app map, and the places along it (water, parks, woods) decide which creatures of the journal's bestiary the run meets. |
| `READ_DISTANCE` | Distance of each run: totals, pace, records and goals. |
| `READ_ELEVATION_GAINED` | Climb of each run: the climb totals, the climbing goals and abilities. |
| `READ_HEART_RATE` | Heart rate during runs gives the training load (TRIMP), heart-rate zones and the share of easy running. |
| `READ_RESTING_HEART_RATE` | Morning resting heart rate shows the user's condition and suggests a rest day when it is up. |
| `READ_HEART_RATE_VARIABILITY` | HRV is shown next to the resting pulse in the condition and summary charts. |
| `READ_STEPS` | Daily steps are shown against the user's step goal and count towards the walking ability. |
| `READ_HEALTH_DATA_HISTORY` | The first read starts on the day of connecting; history is not pulled. Older data is read only to add routes to runs already in the journal when the route permission is given later, and to fill a gap when the app was not opened for more than 30 days. |
| `READ_HEALTH_DATA_IN_BACKGROUND` | Only if the user turns on background reading in Settings → Health Connect: new runs and the morning pulse are read about hourly, so the journal is up to date when opened. Off by default. |

Общая фраза для формы: *Ranger is a running journal. It only reads this data, writes nothing to Health
Connect, and the data never leaves the device (no server, accounts, analytics or ads).*
Ссылка на политику: PRIVACY.md (раздел о Health Connect и Limited Use есть).

**Data safety**

Разработчику не уходит ничего, но Play считает «сбором» любую передачу с телефона, в том числе в чужие
сервисы. Данные о здоровье и точные треки остаются на телефоне. Наружу уходит лишь примерное место:
тайлы карты OpenFreeMap (квадраты ~2,5 км) и точка старта, округлённая до 0,01°, для погоды Open-Meteo.

- *Does your app collect or share any of the required user data types?* → **Yes**.
- *Is all of the user data collected by your app encrypted in transit?* → **Yes** (всё по HTTPS).
- *Do you provide a way for users to request that their data is deleted?* → **No** (аккаунтов нет,
  на серверах ничего не хранится; данные удаляются вместе с приложением).
- Типы данных:
  - **Location → Approximate location** — Collected: **Yes**. Shared: **No** (передача сервису, который
    выполняет функцию приложения, — не «sharing»). **Processed ephemerally: Yes**.
    Required: **No** (без сети приложение работает, только без карты и погоды). Purpose: **App functionality**.
  - **Precise location**, **Health and fitness**, **App activity** (журнал музыки) — *не* отмечать:
    с телефона они не уходят.
- Аналитики, рекламы, идентификаторов, отчётов о сбоях нет. Системное резервное копирование Android
  делает система, а не приложение, его не декларируем.
- Итог в карточке: «Collects: Location», «No data shared with third parties», «Data is encrypted in transit».

**Advertising ID** — *No* (рекламы и SDK аналитики нет, разрешения `AD_ID` в сборке нет).

**Foreground service permissions** (появится после загрузки AAB: targetSdk 36 и `FOREGROUND_SERVICE_LOCATION`)
- Тип: **Location** → задача про отслеживание тренировки (fitness / exercise tracking; если такого пункта
  нет — **Other**). Описание:
  > When the user taps "Record a patrol", Ranger records the run with GPS through a location foreground
  > service with a visible, ongoing notification, so the route and distance keep being recorded while the
  > screen is off or the phone is in a pocket. It starts only on the user's tap, stops when they stop the
  > recording, and the track never leaves the device.
- Ссылка на видео — см. раздел 0.

**Location permissions** — отдельной декларации не нужно: `ACCESS_BACKGROUND_LOCATION` не запрашивается,
местоположение берётся только в записи, начатой пользователем, с уведомлением.

**Прочие разрешения**
- `BIND_NOTIFICATION_LISTENER_SERVICE` (журнал музыки) — формы декларации нет, но ревьюер может спросить:
  доступ даётся пользователем вручную в системных настройках, только после включения функции; читаются
  лишь метаданные трека из медиасессии плеера (исполнитель, название), уведомления не читаются.
- `POST_NOTIFICATIONS` (уведомление записи), `INTERNET` — особых деклараций не нужно.

## 5. Pricing & distribution

- Countries / regions: все доступные.
- Free, без встроенных покупок.

## 6. Релиз

1. Testing → **Closed testing** (для нового аккаунта; иначе Internal testing) → Create new release.
2. Загрузить `Ranger-v<версия>.aab` из GitHub Release (первый релиз с AAB — 1.4.0).
3. Release name — номер версии. Release notes:

```
<en-US>
(listing/en-US/release_notes.txt)
</en-US>
<ru-RU>
(listing/ru-RU/release_notes.txt)
</ru-RU>
```

4. Review release → Start rollout. Первая проверка Google обычно занимает от нескольких дней до недели;
   с Health Connect — дольше: декларацию Health Connect проверяют отдельно, а фоновое чтение и историю —
   особенно придирчиво.
5. После установки из Play проверьте: Health Connect → разрешения → пробежки подтянулись при открытии;
   экран разрешений Health Connect → «What Ranger reads» → ссылка на политику открывается;
   «Записать дозор» показывает уведомление; Настройки → Язык → Русский/English — экран переключается сразу.

## Следующие версии

- `release_notes.txt` переписать под новую версию (≤500 символов — `play.yml` это проверяет;
  для первой там «First release on Google Play» и новинка из [Unreleased]). Дальше релиз уходит в Play сам
  через `play.yml` после подтверждения в окружении `google-play`.
- Скриншоты пересобирать, только если поменялись показанные экраны.

## Риск: OpenFreeMap и Open-Meteo

Оба бесплатны и без ключей; тайлы запрашиваются с собственным User-Agent (`Tiles.AGENT`). Тайлы
скачиваются один раз на район и хранятся на телефоне, погода — один запрос на место и год.
Open-Meteo бесплатен для некоммерческого использования (CC BY 4.0, атрибуция есть в README).

## После публикации

Добавьте в README бейдж и ссылку `https://play.google.com/store/apps/details?id=app.ranger`.
