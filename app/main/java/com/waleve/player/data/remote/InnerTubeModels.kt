package com.waleve.player.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request Models ─────────────────────────────────────────

@Serializable
data class InnerTubeBody(
    val context: InnerTubeContext,
    val query: String? = null,
    val videoId: String? = null,
    val params: String? = null,
)

@Serializable
data class InnerTubeContext(
    val client: InnerTubeClient,
)

@Serializable
data class InnerTubeClient(
    val clientName: String = "WEB_REMIX",
    val clientVersion: String = "1.20241106.01.00",
    val hl: String = "en",
    val gl: String = "US",
)

// ─── Search Response Models ────────────────────────────────

@Serializable
data class SearchResponse(
    val contents: SearchContents? = null,
)

@Serializable
data class SearchContents(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null,
)

@Serializable
data class TabbedSearchResultsRenderer(
    val tabs: List<SearchTab>? = null,
)

@Serializable
data class SearchTab(
    val tabRenderer: TabRenderer? = null,
)

@Serializable
data class TabRenderer(
    val content: TabContent? = null,
)

@Serializable
data class TabContent(
    val sectionListRenderer: SectionListRenderer? = null,
)

@Serializable
data class SectionListRenderer(
    val contents: List<SectionContent>? = null,
)

@Serializable
data class SectionContent(
    val musicShelfRenderer: MusicShelfRenderer? = null,
)

@Serializable
data class MusicShelfRenderer(
    val contents: List<MusicShelfItem>? = null,
)

@Serializable
data class MusicShelfItem(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
)

@Serializable
data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val overlay: Overlay? = null,
    val playlistItemData: PlaylistItemData? = null,
)

@Serializable
data class PlaylistItemData(
    val videoId: String? = null,
)

@Serializable
data class Overlay(
    val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer? = null,
)

@Serializable
data class MusicItemThumbnailOverlayRenderer(
    val content: OverlayContent? = null,
)

@Serializable
data class OverlayContent(
    val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null,
)

@Serializable
data class MusicPlayButtonRenderer(
    val playNavigationEndpoint: PlayNavigationEndpoint? = null,
)

@Serializable
data class PlayNavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
)

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
)

@Serializable
data class FlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer? = null,
)

@Serializable
data class FlexColumnRenderer(
    val text: TextRuns? = null,
)

@Serializable
data class TextRuns(
    val runs: List<TextRun>? = null,
)

@Serializable
data class TextRun(
    val text: String? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
)

@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
)

@Serializable
data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
)

@Serializable
data class MusicThumbnailRenderer(
    val thumbnail: ThumbnailData? = null,
)

@Serializable
data class ThumbnailData(
    val thumbnails: List<Thumbnail>? = null,
)

@Serializable
data class Thumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

// ─── Player Response Models ────────────────────────────────

@Serializable
data class PlayerResponse(
    val videoDetails: VideoDetails? = null,
    val streamingData: StreamingData? = null,
)

@Serializable
data class VideoDetails(
    val videoId: String? = null,
    val title: String? = null,
    val lengthSeconds: String? = null,
    val channelId: String? = null,
    val shortDescription: String? = null,
    val thumbnail: ThumbnailData? = null,
    val author: String? = null,
)

@Serializable
data class StreamingData(
    val adaptiveFormats: List<AdaptiveFormat>? = null,
    val formats: List<AdaptiveFormat>? = null,
)

@Serializable
data class AdaptiveFormat(
    val itag: Int? = null,
    val url: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val contentLength: String? = null,
    val quality: String? = null,
    val qualityLabel: String? = null,
    val audioQuality: String? = null,
    @SerialName("approxDurationMs") val approxDurationMs: String? = null,
    val audioSampleRate: String? = null,
    val audioChannels: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)
