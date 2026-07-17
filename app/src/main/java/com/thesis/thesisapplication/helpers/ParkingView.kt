package com.thesis.thesisapplication.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.withRotation
import com.thesis.thesisapplication.R
import org.json.JSONObject
import java.io.InputStream
import java.util.LinkedList
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ParkingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var isRunning = false
    private var gameThread: Thread? = null

    private var tileSize = 0f
    private val missingPaint = Paint().apply { color = Color.MAGENTA }

    private val masterTileset: Bitmap
    private val carBitmap: Bitmap
    val myCar: Car

    private var mapWidth = 0
    private var mapHeight = 0
    private val mapLayers = mutableListOf<IntArray>()

    companion object {
        private val ROAD_TILES = setOf(4330, 4334)
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
        carBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.car_blue_3, options)

        myCar = Car(carBitmap, 0f, 0f)

        for (row in atlasMapping.indices) {
            for (col in atlasMapping[row].indices) {
                val oldId = atlasMapping[row][col]
                val calculatedIndex = (row * 4) + col
                idToAtlasIndex[oldId] = calculatedIndex
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

    private fun isWalkable(gridX: Int, gridY: Int): Boolean {
        if (gridX !in 0 until mapWidth || gridY !in 0 until mapHeight) return false
        val tileIndex = gridY * mapWidth + gridX
        for (layer in mapLayers) {
            if (layer[tileIndex] in ROAD_TILES) return true
        }
        return false
    }

    private fun findPath(startX: Int, startY: Int, targetX: Int, targetY: Int): List<GridPoint> {
        val queue = LinkedList<List<GridPoint>>()
        val visited = mutableSetOf<GridPoint>()

        val startPoint = GridPoint(startX, startY)
        queue.add(listOf(startPoint))
        visited.add(startPoint)

        val directions = listOf(
            GridPoint(0, -1), GridPoint(0, 1), GridPoint(-1, 0), GridPoint(1, 0)
        )

        while (queue.isNotEmpty()) {
            val currentPath = queue.poll() ?: continue
            val current = currentPath.last()

            if (current.x == targetX && current.y == targetY) return currentPath

            for (dir in directions) {
                val nextX = current.x + dir.x
                val nextY = current.y + dir.y
                val nextPoint = GridPoint(nextX, nextY)

                if (isWalkable(nextX, nextY) && !visited.contains(nextPoint)) {
                    visited.add(nextPoint)
                    val newPath = currentPath.toMutableList()
                    newPath.add(nextPoint)
                    queue.add(newPath)
                }
            }
        }
        return emptyList()
    }

    private fun commandCarToPark(tapGridX: Int, tapGridY: Int) {
        var entranceX = -1
        var entranceY = -1

        for (yOffset in -2..2) {
            val checkY = tapGridY + yOffset
            if (checkY in 0 until mapHeight) {
                for (xOffset in -2..2) {
                    val checkX = tapGridX + xOffset
                    if (checkX in 0 until mapWidth) {
                        val tileIndex = checkY * mapWidth + checkX
                        for (layer in mapLayers) {
                            if (layer[tileIndex] == PARKING_SPOT_ID) {
                                entranceX = checkX
                                entranceY = checkY
                                break
                            }
                        }
                    }
                    if (entranceX != -1) break
                }
            }
            if (entranceX != -1) break
        }

        if (entranceX == -1) {
            Log.e("ParkingView", "No parking spot found near the tap!")
            return
        }

        var roadTargetX = entranceX
        while (roadTargetX >= 0) {
            var isRoad = false
            for (layer in mapLayers) {
                if (layer[entranceY * mapWidth + roadTargetX] in ROAD_TILES) {
                    isRoad = true
                    break
                }
            }
            if (isRoad) break
            roadTargetX--
        }

        val startGridX = (myCar.x / tileSize).toInt().coerceIn(0, mapWidth - 1)
        val startGridY = (myCar.y / tileSize).toInt().coerceIn(0, mapHeight - 1)

        val path = findPath(startGridX, startGridY, roadTargetX, entranceY)

        if (path.isNotEmpty()) {
            val pixelWaypoints = mutableListOf<PointF>()

            for (point in path) {
                val pixelX = (point.x * tileSize) + (tileSize / 2)
                val pixelY = (point.y * tileSize) + (tileSize / 2)
                pixelWaypoints.add(PointF(pixelX, pixelY))
            }

            val entrancePixelX = (entranceX * tileSize) + (tileSize / 2)
            val parkingPixelY = (entranceY * tileSize) + (tileSize / 2)
            pixelWaypoints.add(PointF(entrancePixelX, parkingPixelY))

            val deepParkPixelX = entrancePixelX + (tileSize * 2.0f)
            pixelWaypoints.add(PointF(deepParkPixelX, parkingPixelY))

            myCar.waypoints.clear()
            myCar.waypoints.addAll(pixelWaypoints)
        }
    }

    private fun spawnCarAtTop() {
        val spawnRowY = 1
        var leftMostRoadX = 11

        for (x in 0 until mapWidth) {
            val tileIndex = spawnRowY * mapWidth + x
            if (tileIndex < mapWidth * mapHeight) {
                var foundRoad = false
                for (layer in mapLayers) {
                    if (layer[tileIndex] in ROAD_TILES) {
                        leftMostRoadX = x
                        foundRoad = true
                        break
                    }
                }
                if (foundRoad) break
            }
        }

        myCar.x = (leftMostRoadX + 1.5f) * tileSize
        myCar.y = (spawnRowY + 0.5f) * tileSize
        myCar.rotationDegrees = 180f
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
            commandCarToPark(tapGridX, tapGridY)
        }
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        gameThread = Thread(this)
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (mapWidth > 0) {
            tileSize = width.toFloat() / mapWidth
            spawnCarAtTop()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (_: InterruptedException) { }
        }
    }

    override fun run() {
        var lastTime = System.currentTimeMillis()

        while (isRunning) {
            val now = System.currentTimeMillis()
            val dt = (now - lastTime) / 1000f
            lastTime = now

            myCar.update(dt)
            draw()

            Thread.sleep(16)
        }
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
                    } else {
                        canvas.drawRect(destRect, missingPaint)
                    }
                }
            }

            myCar.draw(canvas)

        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun getSpriteData(oldId: Int): Rect? {
        val sliceSize = 16
        val imageColumns = 4
        val localId = idToAtlasIndex[oldId] ?: return null
        val cropX = (localId % imageColumns) * sliceSize
        val cropY = (localId / imageColumns) * sliceSize
        return Rect(cropX, cropY, cropX + sliceSize, cropY + sliceSize)
    }
}

// --- UPDATED CAR CLASS ---
class Car(
    private val carImage: Bitmap,
    var x: Float,
    var y: Float
) {
    var rotationDegrees: Float = 180f
    var speed: Float = 0f

    // CopyOnWriteArrayList prevents threading crashes!
    val waypoints = CopyOnWriteArrayList<PointF>()
    private val autoPilotSpeed: Float = 200f

    fun update(dt: Float) {
        if (waypoints.isNotEmpty()) {
            val target = waypoints.firstOrNull() ?: return

            val dx = target.x - x
            val dy = target.y - y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (distance < 15f) {
                waypoints.remove(target)
                if (waypoints.isEmpty()) {
                    speed = 0f
                    rotationDegrees = 90f
                }
            } else {
                val targetAngleRadians = atan2(dy.toDouble(), dx.toDouble())
                rotationDegrees = Math.toDegrees(targetAngleRadians).toFloat() + 90f
                speed = autoPilotSpeed
            }
        } else {
            speed = 0f
        }

        val radians = Math.toRadians(rotationDegrees.toDouble())
        x += (speed * sin(radians) * dt).toFloat()
        y -= (speed * cos(radians) * dt).toFloat()
    }

    fun draw(canvas: Canvas) {
        val sizeIncrease = 3f
        val carWidth = carImage.width.toFloat() + sizeIncrease
        val carHeight = carImage.height.toFloat() + sizeIncrease

        val left = x - (carWidth / 2)
        val top = y - (carHeight / 2)
        val destRect = RectF(left, top, left + carWidth, top + carHeight)

        canvas.withRotation(rotationDegrees, x, y) {
            drawBitmap(carImage, null, destRect, null)
        }
    }
}