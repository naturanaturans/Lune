# Plan de implementación — Search Actions + Smart Playlists

## Feature 1: Play / Shuffle All en búsqueda

### Archivos a modificar

| Archivo | Cambio |
|---------|--------|
| `app/src/main/java/com/demonlab/lune/ui/search/SearchScreen.kt` | Agregar header con botones Play/Shuffle arriba de la sección "All" |
| `app/src/main/java/com/demonlab/lune/ui/activities/Lune.kt` | Pasar callbacks `onPlayAll` / `onShuffleAll` a `SearchScreen`, o reutilizar `onSongClick` |

### Lógica

```kotlin
// En SearchScreen.kt, dentro de LazyColumn, antes del item "All":
if (searchResults.songs.size > 1) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = { onPlayAll(searchResults.songs) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play All")
            }
            FilledTonalButton(
                onClick = { onShuffleAll(searchResults.songs) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shuffle All")
            }
        }
    }
}
```

### Callbacks nuevos en `SearchScreen`

```kotlin
onPlayAll: (List<Song>) -> Unit,
onShuffleAll: (List<Song>) -> Unit,
```

### En `Lune.kt`

```kotlin
onPlayAll = { songs ->
    val first = songs.first()
    playbackManager.play(first, songs, playlistId = -100L, category = "ALL")
    onCurrentSongChange(first)
    onIsPlayingChange(true)
    onSelectedFolderChange("ALL")
    showSearchScreen = false
},
onShuffleAll = { songs ->
    if (!playbackManager.isShuffle) playbackManager.toggleShuffle()
    val shuffled = songs.shuffled()
    val first = shuffled.first()
    playbackManager.play(first, songs, playlistId = -100L, category = "ALL")
    onCurrentSongChange(first)
    onIsPlayingChange(true)
    onSelectedFolderChange("ALL")
    showSearchScreen = false
},
```

### Strings nuevos (en `strings.xml`)
- `search_play_all` = "Play All"
- `search_shuffle_all` = "Shuffle All"

---

## Feature 2: Smart Playlists

### Índice de cambios

```
NUEVOS:
  app/src/main/java/com/demonlab/lune/data/SmartPlaylist.kt     ← entidad + DAO
  app/src/main/java/com/demonlab/lune/data/PlaybackEvent.kt      ← entidad + DAO
  app/src/main/java/com/demonlab/lune/ui/smartplaylist/
    SmartPlaylistEngine.kt                                       ← lógica de evaluación
    SmartPlaylistCard.kt                                         ← UI para mostrar en lista
  app/src/main/java/com/demonlab/lune/ui/screens/resume/SmartPlaylistSection.kt  ← sección en ResumeScreen

MODIFICADOS:
  app/src/main/java/com/demonlab/lune/data/MusicDatabase.kt     ← +entities, +DAOs, +migration
  app/src/main/java/com/demonlab/lune/tools/PlaybackManager.kt  ← conteo basado en duración, registrar PlaybackEvent
  app/src/main/java/com/demonlab/lune/ui/viewmodels/MusicViewModel.kt  ← smart playlists flows
  app/src/main/java/com/demonlab/lune/ui/screens/ResumeScreen.kt  ← mostrar smart playlists
  app/src/main/java/com/demonlab/lune/ui/playlist/PlaylistViews.kt  ← smart playlists en lista
```

---

### Paso 1 — Nueva entidad `PlaybackEvent`

**Archivo:** `app/src/main/java/com/demonlab/lune/data/PlaybackEvent.kt`

Cada vez que una canción pasa el threshold de "escuchada" (ej. 30s o 50% de duración), se inserta una fila.

```kotlin
@Entity(tableName = "playback_events")
data class PlaybackEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long,       // cuánto se escuchó en este evento
    val percentagePlayed: Float       // 0.0..1.0
)

@Dao
interface PlaybackEventDao {
    @Query("SELECT * FROM playback_events ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<PlaybackEvent>>

    @Query("SELECT * FROM playback_events WHERE songId = :songId ORDER BY timestamp DESC")
    fun getEventsForSong(songId: Long): Flow<List<PlaybackEvent>>

    @Query("SELECT * FROM playback_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getEventsSince(since: Long): Flow<List<PlaybackEvent>>

    @Query("SELECT COUNT(DISTINCT songId) FROM playback_events WHERE timestamp >= :since")
    suspend fun countUniqueSongsSince(since: Long): Int

    @Query("SELECT songId, COUNT(*) as count FROM playback_events GROUP BY songId ORDER BY count DESC")
    fun getPlayCountsFlow(): Flow<List<SongPlayCount>>

    @Insert
    suspend fun insert(event: PlaybackEvent)

    @Query("DELETE FROM playback_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class SongPlayCount(
    val songId: Long,
    val count: Int
)
```

### Paso 2 — Umbral de "escuchada" en `PlaybackManager`

El conteo actual (`playCount`) se incrementa al **iniciar** reproducción (líneas 298 y 537 de PlaybackManager.kt). Para smart playlists se necesita un segundo contador basado en **duración escuchada**.

Estrategia: No eliminar el conteo actual (no romper stats existentes). Agregar lógica paralela:

```kotlin
// En flushPendingStats(), después de updatePlaybackStats(...) actual:
private val PLAY_THRESHOLD_MS = 30_000L  // 30 segundos

// Verificar threshold y registrar PlaybackEvent
if (pendingStatsTimeMs >= PLAY_THRESHOLD_MS || pendingStatsTimeMs >= song.durationMs / 2) {
    val event = PlaybackEvent(
        songId = song.id,
        durationPlayedMs = pendingStatsTimeMs,
        percentagePlayed = pendingStatsTimeMs.toFloat() / song.durationMs.coerceAtLeast(1)
    )
    scope.launch {
        database.playbackEventDao().insert(event)
        // Incrementar playCount REAL (solo cuando pasa threshold)
        val stats = database.playbackStatsDao().getStatsById("SONG_${song.id}")
        if (stats != null) {
            database.playbackStatsDao().insertStats(stats.copy(
                playCount = stats.playCount + 1,
                lastPlayed = System.currentTimeMillis()
            ))
        }
    }
}
```

Pero esto requiere acceso a la DB desde PlaybackManager. Actualmente PlaybackManager no tiene referencia a `MusicDatabase`. Habría que inyectarla o pasarla vía `getInstance()`.

**Opción**: Agregar `database` como parámetro en `PlaybackManager.getInstance(context, database)` o usar `MusicDatabase.getDatabase(context)` internamente.

### Paso 3 — Nueva entidad `SmartPlaylist`

**Archivo:** `app/src/main/java/com/demonlab/lune/data/SmartPlaylist.kt`

```kotlin
@Entity(tableName = "smart_playlists")
data class SmartPlaylistDefinition(
    @PrimaryKey val type: String,  // "MOST_PLAYED", "RECENTLY_PLAYED", "NOT_PLAYED"
    val name: String,
    val isVisible: Boolean = true,
    val configJson: String = "{}"  // para parámetros como límite de días, umbral de count, etc.
)

@Dao
interface SmartPlaylistDao {
    @Query("SELECT * FROM smart_playlists WHERE isVisible = 1")
    fun getVisibleFlow(): Flow<List<SmartPlaylistDefinition>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: SmartPlaylistDefinition)
}
```

### Paso 4 — Motor de evaluación

**Archivo:** `app/src/main/java/com/demonlab/lune/ui/smartplaylist/SmartPlaylistEngine.kt`

```kotlin
class SmartPlaylistEngine(
    private val allSongs: List<Song>,
    private val playbackEventDao: PlaybackEventDao,
    private val playbackStatsDao: PlaybackStatsDao
) {
    fun evaluateMostPlayed(limit: Int = 50): List<Song> {
        // Top N canciones por count de eventos que pasaron threshold
    }

    suspend fun evaluateRecentlyPlayed(days: Int = 7): List<Song> {
        // Canciones con eventos en los últimos N días
    }

    suspend fun evaluateNotPlayed(): List<Song> {
        // Canciones sin ningún PlaybackEvent
    }

    fun getCount(definition: SmartPlaylistDefinition): Int {
        // Cantidad de canciones que cumplen el criterio
    }
}
```

### Paso 5 — Registrar `PlaybackEvent`

En `PlaybackManager.kt`, dentro de `stop()`, `pause()`, y antes de `play()` (cuando se cambia de canción):

1. Verificar si `pendingStatsTimeMs >= PLAY_THRESHOLD_MS || pendingStatsTimeMs >= song.durationMs / 2`
2. Si pasa threshold, insertar `PlaybackEvent`
3. Actualizar `PlaybackStats.playCount` real (solo cuando pasa threshold)

**Nuevo import:** `com.demonlab.lune.data.MusicDatabase`

### Paso 6 — UI: Smart Playlists en lista

**ResumeScreen.kt**: Agregar sección "Smart Playlists" (Most Played, Recently Played, Not Played) con count de canciones.

**PlaylistViews.kt**: Agregar smart playlists al inicio del LazyColumn, con estilo diferente (icono + nombre + count). Al hacer clic, abrir vista de contenido (puede reutilizar `PlaylistDetailView` o un overlay similar).

### Paso 7 — UI: Vista de contenido de smart playlist

Reutilizar `PlaylistDetailView` o crear un overlay similar que muestre los resultados de `SmartPlaylistEngine.evaluate*()` y permita hacer clic para reproducir.

### Paso 8 — Strings nuevos

| key | value |
|-----|-------|
| `smart_most_played` | "Most Played" |
| `smart_recently_played` | "Recently Played" |
| `smart_not_played` | "Not Played" |
| `smart_song_count` | "%d songs" |

---

## Orden de implementación sugerido

```
Semana 1: Search Play/Shuffle All  (Feature 1)
  Día 1: PlaybackEvent entity + DAO
  Día 2: Lógica de threshold en PlaybackManager + registrar eventos
  Día 3: SmartPlaylistDefinition entity + DAO + SmartPlaylistEngine
  Día 4: SmartPlaylistEngine (evaluación)
  Día 5: UI smart playlists en ResumeScreen + PlaylistViews
  Día 6: UI vista de contenido smart playlist
  Día 7: Integración final + pruebas + strings

Feature 1 (Search) es independiente y se puede hacer en cualquier orden; no depende de ninguna otra pieza.
Feature 2 (Smart Playlists) requiere completar Pasos 1-2 antes de 3-4, y 5-7 son UI que depende de 3-4.
```
