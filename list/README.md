# Default account list

The list that ships with the app is `app/src/main/assets/default_accounts.txt`: one
username per line, lowercase, sorted, no duplicates. It is the only copy the app reads.

| File | Content |
|---|---|
| `sources.tsv` | Every username in the default list, with the source(s) it came from |
| `sources/blockout_extremedroitefrance_following.txt` | Accounts followed by @blockout_extremedroitefrance, as collected |

Source tags used in `sources.tsv`:

| Tag | Source |
|---|---|
| `following` | Accounts followed by [@blockout_extremedroitefrance](https://www.instagram.com/blockout_extremedroitefrance/) |
| `pasted` | The list at [trace-archive.fr/onbloque](https://trace-archive.fr/onbloque) |
| `wikidata` | Instagram usernames recorded on [Wikidata](https://www.wikidata.org/) for members of French far-right parties and for the parties themselves |
| `assemblee` | Instagram accounts declared by RN and UDR members of the [Assemblée nationale](https://data.assemblee-nationale.fr/) |
| `blockout_ed` | [Vaalgtir/blockout_ed](https://github.com/Vaalgtir/blockout_ed) |
| `onbloque_github` | [juliencoquet/onbloquelextremedroite](https://github.com/juliencoquet/onbloquelextremedroite) |
| `onbloque_site` | [onbloquelextremedroite.fr](https://www.onbloquelextremedroite.fr/) |

## Updating the list

1. Edit `app/src/main/assets/default_accounts.txt`, keeping it sorted and free of duplicates.
2. Add the new usernames to `sources.tsv`.
3. Bump `DEFAULT_LIST_VERSION` in `app/src/main/java/dev/instablock/data/Store.kt`, so
   installed apps load the new list once on their next start.
