package com.thesis.thesisapplication.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.thesis.thesisapplication.R
import org.json.JSONObject
import java.io.InputStream


class ParkingSelectorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var isRunning = false
    private var gameThread: Thread? = null

    private var tileSize = 0f
    private val masterTileset: Bitmap

    private var mapWidth = 0
    private var mapHeight = 0
    private val mapLayers = mutableListOf<IntArray>()

    // --- SELECTION VARIABLES ---
    @Volatile private var selectedX: Int = -1
    @Volatile private var selectedY: Int = -1
    @Volatile private var isSelectedOccupied = false

    // --- ANIMATION VARIABLES ---
    @Volatile private var highlightScale = 1.0f
    @Volatile private var highlightAlpha = 0f

    // Modern Highlight Paints (Fill + Stroke for a sleek target-box look)
    private val greenFill = Paint().apply { color = Color.rgb(76, 175, 80); style = Paint.Style.FILL }
    private val redFill = Paint().apply { color = Color.rgb(244, 67, 54); style = Paint.Style.FILL }
    private val greenStroke = Paint().apply { color = Color.rgb(76, 175, 80); style = Paint.Style.STROKE; strokeWidth = 8f }
    private val redStroke = Paint().apply { color = Color.rgb(244, 67, 54); style = Paint.Style.STROKE; strokeWidth = 8f }

    interface OnSlotSelectedListener {
        fun onSlotSelected(x: Int, y: Int, isOccupied: Boolean)
    }
    var slotListener: OnSlotSelectedListener? = null

    companion object {
        private const val PARKING_SPOT_ID = 4426
    }

    private val atlasMapping = arrayOf(
        intArrayOf(59, 424, 4330, 4331), intArrayOf(253, 4333, 4334, 4335),
        intArrayOf(252, 4337, 4342, 3953), intArrayOf(254, 490, 4338, 4343),
        intArrayOf(251, 486, 4426, 4347), intArrayOf(292, 4349, 4430),
        intArrayOf(295, 4353), intArrayOf(178, 4357),
        intArrayOf(293, 4361), intArrayOf(331), intArrayOf(320),
        intArrayOf(322), intArrayOf(278), intArrayOf(276),
        intArrayOf(277), intArrayOf(318), intArrayOf(317),
        intArrayOf(288), intArrayOf(307), intArrayOf(289)
    )

    private val idToAtlasIndex = mutableMapOf<Int, Int>()

    init {
        holder.addCallback(this)
        val options = BitmapFactory.Options().apply { inScaled = false }
        masterTileset = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_used, options)

        for (row in atlasMapping.indices) {
            for (col in atlasMapping[row].indices) {
                idToAtlasIndex[atlasMapping[row][col]] = (row * 4) + col
            }
        }
        loadMapFromJSON()
    }

    @Suppress("SpellCheckingInspection")
    private fun loadMapFromJSON() {
        try {
            val inputStream: InputStream = context.resources.openRawResource(R.raw.parking_map)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            mapWidth = jsonObject.getInt("width")
            mapHeight = jsonObject.getInt("height")
            val layersArray = jsonObject.getJSONArray("layers")
            mapLayers.clear()

            for (i in 0 until layersArray.length()) {
                val layer = layersArray.getJSONObject(i)
                if (layer.getString("type") == "tilelayer") {
                    val dataArray = layer.getJSONArray("data")
                    val layerData = IntArray(dataArray.length()) { dataArray.getInt(it) }
                    mapLayers.add(layerData)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()

            val tapGridX = (event.x / tileSize).toInt()
            val tapGridY = (event.y / tileSize).toInt()

            var found = false
            for (yOffset in -1..1) {
                for (xOffset in -1..1) {
                    val checkX = tapGridX + xOffset
                    val checkY = tapGridY + yOffset
                    if (checkX in 0 until mapWidth && checkY in 0 until mapHeight) {
                        for (layer in mapLayers) {
                            if (layer[checkY * mapWidth + checkX] == PARKING_SPOT_ID) {
                                selectSlotProgrammatically(checkX, checkY)
                                found = true
                                break
                            }
                        }
                    }
                    if (found) break
                }
                if (found) break
            }
        }
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        gameThread = Thread(this)
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        tileSize = width.toFloat() / mapWidth
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (_: InterruptedException) {}
        }
    }

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (isRunning) {
            val now = System.currentTimeMillis()
            val dt = (now - lastTime) / 1000f
            lastTime = now

            updateAnimation(dt)
            draw()

            Thread.sleep(16)
        }
    }

    private fun updateAnimation(dt: Float) {
        // Smoothly interpolates the scale and alpha for that modern pop-in effect
        highlightScale += (1.0f - highlightScale) * 15f * dt
        highlightAlpha += (1.0f - highlightAlpha) * 15f * dt
    }

    private fun draw() {
        if (!holder.surface.isValid) return
        val canvas = holder.lockCanvas()
        try {
            canvas.drawColor(0xFF1A1A1A.toInt())

            for (layer in mapLayers) {
                for (i in layer.indices) {
                    val tileId = layer[i]
                    if (tileId == 0) continue

                    val x = i % mapWidth
                    val y = i / mapWidth
                    val destRect = RectF(x * tileSize, y * tileSize, (x + 1) * tileSize, (y + 1) * tileSize)

                    val sourceRect = getSpriteData(tileId)
                    if (sourceRect != null) {
                        canvas.drawBitmap(masterTileset, sourceRect, destRect, null)
                    }
                }
            }

            // --- MODERN 5x3 ANIMATED HIGHLIGHT ---
            if (selectedX != -1 && selectedY != -1) {
                val centerX = (selectedX + 2.5f) * tileSize
                val centerY = (selectedY + 0.5f) * tileSize

                val baseWidth = 5f * tileSize
                val baseHeight = 3f * tileSize

                val currentWidth = baseWidth * highlightScale
                val currentHeight = baseHeight * highlightScale

                val highlightRect = RectF(
                    centerX - currentWidth / 2f,
                    centerY - currentHeight / 2f,
                    centerX + currentWidth / 2f,
                    centerY + currentHeight / 2f
                )

                val currentFillAlpha = (100f * highlightAlpha).toInt().coerceIn(0, 255)
                val currentStrokeAlpha = (255f * highlightAlpha).toInt().coerceIn(0, 255)

                val fillPaint = if (isSelectedOccupied) redFill else greenFill
                val strokePaint = if (isSelectedOccupied) redStroke else greenStroke

                fillPaint.alpha = currentFillAlpha
                strokePaint.alpha = currentStrokeAlpha

                val cornerRadius = 16f
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, fillPaint)
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, strokePaint)
            }

        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun getSpriteData(oldId: Int): Rect? {
        val localId = idToAtlasIndex[oldId] ?: return null
        val cropX = (localId % 4) * 16
        val cropY = (localId / 4) * 16
        return Rect(cropX, cropY, cropX + 16, cropY + 16)
    }

    fun getAllParkingSlots(): List<GridPoint> {
        val slots = mutableListOf<GridPoint>()
        for (y in 0 until mapHeight) {
            for (x in 0 until mapWidth) {
                val tileIndex = y * mapWidth + x
                for (layer in mapLayers) {
                    if (layer[tileIndex] == PARKING_SPOT_ID) {
                        slots.add(GridPoint(x, y))
                        break
                    }
                }
            }
        }
        return slots
    }

    fun selectSlotProgrammatically(x: Int, y: Int) {
        selectedX = x
        selectedY = y
        isSelectedOccupied = (x + y) % 2 == 0

        // Reset animation states so it "pops" every time a new slot is selected
        highlightScale = 1.3f
        highlightAlpha = 0.0f

        // Play audio and haptic feedback
        post {
            playSoundEffect(SoundEffectConstants.CLICK)
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        slotListener?.onSlotSelected(selectedX, selectedY, isSelectedOccupied)
    }
}