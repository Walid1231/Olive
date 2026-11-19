## Olive 1.5.5 · Fixes from your screenshots

- **Continue listening follows the player.** While that book is playing it says "Now listening", with a **Pause** button and live progress. **Resume** carries on from where you are instead of restarting.
- If the app ever loses its link to the music service, it reconnects by itself, so play/pause always shows the truth.
- The **"Stories for every mood"** banner no longer cuts off its title.
- **Easier to read on the blurred cover**: a stronger milky layer behind the text, darker secondary text, and shelf labels like **🎧 AUDIOBOOKS** in a solid pill.

### From 1.5.4 — · Online songs play again + tidier screens

### Online songs play & download again
- Since late 2025 YouTube makes downloaders solve a small JavaScript puzzle. Olive already contained a JavaScript engine (QuickJS), but yt-dlp wasn't using it. Now it does.
- Olive first tries YouTube's Android VR route, which needs no puzzle and starts faster. It falls back automatically if that fails.
- If a song can't be streamed, a message says so and the next song plays, instead of nothing happening.

### Songs start faster, and you can see them loading
- Tapping a song now asks YouTube's own player for the audio link directly, usually in under a second (yt-dlp is the backup, and takes over automatically if the quick link fails).
- Links are remembered for a few hours, so replaying is instant.
- yt-dlp no longer updates itself before your first song. It updates once a day, in the background.
- **Loading feedback**: the mini player shows the song you tapped right away, with a spinning ring and "Loading…". The tapped card or row shows a spinner, then bouncing bars while it plays.
- Tapping a search result queues the other results after it.

### 🌙 Dark mode
- **Library → ⚙ → Appearance**: Auto (follows your phone), Light or Dark.
- Dark mode keeps the glassmorphism: smoky glass cards with lit edges over a deep navy background, with the playing song's blurred cover glowing behind everything.
- Every card, sheet, menu, chat bubble, text field and the navigation dock has a dark version. Status-bar icons switch too.

### New font: Outfit
- The whole app now uses **Outfit**, including text fields and photo-card titles.

### Looks
- **Blurred-cover glass**: the cover of the song you're playing becomes a soft, blurred backdrop behind the whole app. All the glass cards float over it, and it fades smoothly when the song changes. With nothing playing, the pastel glass background stays.
- **No more big empty space at the top**: every screen sits higher. The status-bar gap was being added twice.
- **Search**: compact header, a purple **Audiobooks banner** at the top of Discover, an Audiobooks chip with a book icon, and an "🎧 Audiobook" badge on book covers.
- **Library**: Songs and Audiobooks sit in one box. Each side shows an icon, its name and its count ("2 songs", "23 chapters"), with a sliding green pill.
- The count now sits inside the same pill as the label everywhere (no separate number box).

### (1.5.3, combined above)

- **Fixed: online songs wouldn't play or download.** Since late 2025 YouTube makes downloaders solve a small JavaScript puzzle. Olive already contained a JavaScript engine (QuickJS), but yt-dlp wasn't using it. Now it does.
- **Faster start**: Olive first asks YouTube with its Android VR app identity, which doesn't need the puzzle at all. If that fails, it falls back automatically.
- If a song really can't be streamed, a message says so and Olive moves on to the next song, instead of doing nothing.

### From 1.5.2 — · Update check fix

- **"Check for updates" works on every network.** GitHub allows only 60 version checks an hour per internet address, and mobile networks share addresses. When that limit was used up, Olive wrongly said you were on the latest version. It now falls back to a check without that limit.
- If GitHub really can't be reached, it says so instead of "latest version".

### From 1.5.1 — · Audiobooks first, English music

### Discover puts audiobooks on top
- The first shelves in Search are now **Audiobooks for you**, **Bangla audio golpo** and **Sunday Suspense**.
- New **📚 Audiobooks** chip: Popular audiobooks, Classic novels, Mystery & thrillers, Sunday Suspense, Bangla golpo, Bhuter golpo, Sherlock Holmes, Self-help, Sci-fi & fantasy, Bedtime stories.
- Every book card has a **download** button. It saves to Audiobooks automatically for long books.
- Online audiobooks are played as books: they **remember where you stopped**, rewind a little after a break, and keep their own speed.

### English music first
- The music shelves come from the US / English catalogue.
- Bollywood, Hindi, Punjabi, Tamil and other Indian-language shelves are hidden from Discover. You can still search for them any time.
