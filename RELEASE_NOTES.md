## Olive 1.4.0 · Share & Together+

### Songs from friends save themselves
- When a friend sends you a song or audiobook file, Olive saves it to your phone by itself as soon as the app is open, and it shows up in your library.
- The inbox shows **"On your phone"** with a Play button that plays your local copy.
- After everyone it was sent to has saved it, the file is **deleted from the cloud** automatically, so the free storage never fills up. Files nobody picked up are removed after 14 days.
- Switch: **Friends → Friends tab → Auto-save songs friends send**.

### Listen together
- **Everyone can control**: the host can let friends play, pause, skip and seek. Guests can even play any song from their own phone, and it plays for the whole group.
- **Chat inside the session**: messages plus quick ❤️🔥😂😭👏🎶 reactions. The bar above the mini player shows 💬 unread messages.
- **Chats are kept on your phone**. The cloud only passes each message along, and the host's phone wipes them from the cloud when the session ends. Earlier chats are under **Listen together → Earlier chats**.
- If your sleep timer stops the music during a session, you leave the session (instead of the music starting again).

> **Owner setup:** publish the updated Firestore rules (`docs/firestore.rules`): new `transfers` and session `messages` rules, and the "Everyone can control" rule.
