package net.osmand.plus.views;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import net.osmand.LogUtil;
import net.osmand.data.MapTileDownloader;
import net.osmand.data.MapTileDownloader.DownloadRequest;
import net.osmand.data.MapTileDownloader.IMapDownloaderCallback;
import net.osmand.map.IMapLocationListener;
import net.osmand.osm.LatLon;
import net.osmand.osm.MapUtils;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.activities.OsmandApplication;
import net.osmand.plus.views.MultiTouchSupport.MultiTouchZoomListener;
import net.osmand.plus.views.OsmandMapTileView.CenterCircle;
import net.osmand.plus.views.OsmandMapTileView.OsmandMapTileViewRenderer;

import org.apache.commons.logging.Log;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.WindowManager;

public class OsmandMapTileView extends GLSurfaceView implements IMapDownloaderCallback, Callback {

	public class CenterCircle {
		private FloatBuffer circleVB;
		int VERTICIES=20; // more than needed
		
		public CenterCircle() {
	        ByteBuffer vbb = ByteBuffer.allocateDirect(
	                // (# of coordinate values * 4 bytes per float)
	        		VERTICIES * 3 * 4); 
	        vbb.order(ByteOrder.nativeOrder());// use the device hardware's native byte order
	        circleVB = vbb.asFloatBuffer();
	        //the circle
			float coords[] = new float[VERTICIES * 3];
			float theta = 0;
			float radius = 0.05f;
			for (int i = 0; i < VERTICIES * 3; i += 3) {
			  coords[i + 0] = (float) Math.cos(theta)*radius;
			  coords[i + 1] = (float) Math.sin(theta)*radius;
			  coords[i + 2] = 0;
			  theta += Math.PI / (VERTICIES/2);
			}
			circleVB.put(coords);
			circleVB.position(0);
		}

		public void draw(GL10 gl) {
			  gl.glColor4f(0, 0, 1, 0.5f);
			  gl.glVertexPointer(3, GL10.GL_FLOAT, 0, circleVB);
			  gl.glDrawArrays(GL10.GL_LINE_LOOP, 0, VERTICIES);
//			  canvas.drawCircle(w, h, 3 * dm.density, paintCenter);
//			  canvas.drawCircle(w, h, 7 * dm.density, paintCenter);
		}
	}
	
	public class OsmandMapTileViewRenderer implements Renderer {

		private FloatBuffer triangleVB;
		private boolean nightMode = false;
		private CenterCircle centerCircle;

		@Override
		public void onDrawFrame(GL10 gl) {
			//TODO make this setup only if the nightMode changes...
			if(nightMode){
				gl.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
			} else {
				gl.glClearColor(1f, 1f, 1f, 1.0f);
			}
	        // Redraw background color
	        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
	        
	        // Set GL_MODELVIEW transformation mode
	        gl.glMatrixMode(GL10.GL_MODELVIEW);
	        gl.glLoadIdentity();   // reset the matrix to its default state
	        
	        // Create a rotation for the triangle
	        long time = SystemClock.uptimeMillis() % 4000L;
	        float angle = 0.090f * ((int) time);
	        
	        // When using GL_MODELVIEW, you must set the view point
	        GLU.gluLookAt(gl, 0, 0, -5, 0f, 0f, 0f, 0f, 1.0f, 0.0f);    
	        
	        gl.glRotatef(angle, 0.0f, 0.0f, 1.0f);     
	        
	        // Draw the triangle
	        gl.glColor4f(0.63671875f, 0.76953125f, 0.22265625f, 0.0f);
	        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleVB);
	        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 3);
	        
	        drawOverMap(gl, latlonRect, tilesRect, nightMode);
	        
			if (showMapPosition) {
			  centerCircle.draw(gl);
			}

		}

		@Override
		public void onSurfaceChanged(GL10 gl, int width, int height) {
		      gl.glViewport(0, 0, width, height);
		      
		      // make adjustments for screen ratio
		      float ratio = (float) width / height;
		      gl.glMatrixMode(GL10.GL_PROJECTION);        // set matrix to projection mode
		      gl.glLoadIdentity();                        // reset the matrix to its default state
		      gl.glFrustumf(-ratio, ratio, -1, 1, 3, 7);  // apply the projection matrix
		}

		@Override
		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
	        // Set the background frame color
	        gl.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
	        
	        // initialize the triangle vertex array
			initShapes();
			
	        // Enable use of vertex arrays
	        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		}

	    private void initShapes(){
	        
	        float triangleCoords[] = {
	            // X, Y, Z
	            -0.5f, -0.25f, 0,
	             0.5f, -0.25f, 0,
	             0.0f,  0.559016994f, 0
	        }; 
	        
	        // initialize vertex Buffer for triangle  
	        ByteBuffer vbb = ByteBuffer.allocateDirect(
	                // (# of coordinate values * 4 bytes per float)
	                triangleCoords.length * 4); 
	        vbb.order(ByteOrder.nativeOrder());// use the device hardware's native byte order
	        triangleVB = vbb.asFloatBuffer();  // create a floating point buffer from the ByteBuffer
	        triangleVB.put(triangleCoords);    // add the coordinates to the FloatBuffer
	        triangleVB.position(0);            // set the buffer to read the first coordinate
//	        
	        centerCircle = new CenterCircle();
	    }

		public void setNightMode(boolean nightMode) {
			this.nightMode = nightMode;
		}
	}

	protected final int emptyTileDivisor = 16;
	

	public interface OnTrackBallListener {
		public boolean onTrackBallEvent(MotionEvent e);
	}

	public interface OnLongClickListener {
		public boolean onLongPressEvent(PointF point);
	}

	public interface OnClickListener {
		public boolean onPressEvent(PointF point);
	}

	protected static final Log log = LogUtil.getLog(OsmandMapTileView.class);
	/**MapTree
	 * zoom level - could be float to show zoomed tiles
	 */
	private float zoom = 3;

	private double longitude = 0d;

	private double latitude = 0d;

	private float rotate = 0;
	
	private float rotateSin = 0;
	private float rotateCos = 1;

	private int mapPosition;

	private boolean showMapPosition = true;

	private IMapLocationListener locationListener;

	private OnLongClickListener onLongClickListener;

	private OnClickListener onClickListener;

	private OnTrackBallListener trackBallDelegate;

	private List<OsmandMapLayer> layers = new ArrayList<OsmandMapLayer>();
	
	private BaseMapLayer mainLayer;
	
	private Map<OsmandMapLayer, Float> zOrders = new HashMap<OsmandMapLayer, Float>();

	// UI Part
	// handler to refresh map (in ui thread - ui thread is not necessary, but msg queue is required).
	protected Handler handler = new Handler();

	private AnimateDraggingMapThread animatedDraggingThread;

	private GestureDetector gestureDetector;

	private MultiTouchSupport multiTouchSupport;

	Paint paintGrayFill;
	Paint paintBlackFill;
	Paint paintWhiteFill;
	Paint paintCenter;

	private DisplayMetrics dm;

	private final OsmandApplication application;
	private OsmandMapTileViewRenderer renderer;

	public OsmandMapTileView(Context context, AttributeSet attrs) {
		super(context, attrs);
		application = (OsmandApplication) context.getApplicationContext();
		initView();
		
	}

	public OsmandMapTileView(Context context) {
		super(context);
		application = (OsmandApplication) context.getApplicationContext();
		initView();
	}

	// ///////////////////////////// INITIALIZING UI PART ///////////////////////////////////
	public void initView() {
		paintGrayFill = new Paint();
		paintGrayFill.setColor(Color.GRAY);
		paintGrayFill.setStyle(Style.FILL);
		// when map rotate
		paintGrayFill.setAntiAlias(true);
		
		paintBlackFill= new Paint();
		paintBlackFill.setColor(Color.BLACK);
		paintBlackFill.setStyle(Style.FILL);
		// when map rotate
		paintBlackFill.setAntiAlias(true);

		paintWhiteFill = new Paint();
		paintWhiteFill.setColor(Color.WHITE);
		paintWhiteFill.setStyle(Style.FILL);
		// when map rotate
		paintWhiteFill.setAntiAlias(true);

		paintCenter = new Paint();
		paintCenter.setStyle(Style.STROKE);
		paintCenter.setColor(Color.rgb(60, 60, 60));
		paintCenter.setStrokeWidth(2);
		paintCenter.setAntiAlias(true);

		setClickable(true);
		setLongClickable(true);
		setFocusable(true);

		getHolder().addCallback(this);

		animatedDraggingThread = new AnimateDraggingMapThread(this);
		gestureDetector = new GestureDetector(getContext(), new MapTileViewOnGestureListener());
		multiTouchSupport = new MultiTouchSupport(getContext(), new MapTileViewMultiTouchZoomListener());
		gestureDetector.setOnDoubleTapListener(new MapTileViewOnDoubleTapListener());

		WindowManager mgr = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
		dm = new DisplayMetrics();
		mgr.getDefaultDisplay().getMetrics(dm);
		
		renderer = new OsmandMapTileViewRenderer();
		setRenderer(renderer);
		setRenderMode(RENDERMODE_WHEN_DIRTY);
	}

//	@Override
//	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//		refreshMap();
//	}
//
//	@Override
//	public void surfaceCreated(SurfaceHolder holder) {
//		refreshMap();
//	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
	}

	public void addLayer(OsmandMapLayer layer, float zOrder) {
		int i = 0;
		for (i = 0; i < layers.size(); i++) {
			if (zOrders.get(layers.get(i)) > zOrder) {
				break;
			}
		}
		layer.initLayer(this);
		layers.add(i, layer);
		zOrders.put(layer, zOrder);
	}

	public void removeLayer(OsmandMapLayer layer) {
		layers.remove(layer);
		zOrders.remove(layer);
		layer.destroyLayer();
	}

	public List<OsmandMapLayer> getLayers() {
		return layers;
	}

	public OsmandApplication getApplication() {
		return application;
	}

	// ///////////////////////// NON UI PART (could be extracted in common) /////////////////////////////
	/**
	 * Returns real tile size in pixels for float zoom .  
	 */
	public float getTileSize() {
		float res = getSourceTileSize();
		if (zoom != (int) zoom) {
			res *= (float) Math.pow(2, zoom - (int) zoom);
		}

		// that trigger allows to scale tiles for certain devices
		// for example for device with density > 1 draw tiles the same size as with density = 1
		// It makes text bigger but blurry, the settings could be introduced for that
		if (dm != null && dm.density > 1f && !getSettings().USE_HIGH_RES_MAPS.get() ) {
			res *= dm.density;
		}
		return res;
	}

	public int getSourceTileSize() {
		if(mainLayer instanceof MapTileLayer){
			return ((MapTileLayer) mainLayer).getSourceTileSize();
		}
		return 256;
	}

	/**
	 * @return x tile based on (int) zoom
	 */
	public float getXTile() {
		return (float) MapUtils.getTileNumberX(getZoom(), longitude);
	}

	/**
	 * @return y tile based on (int) zoom
	 */
	public float getYTile() {
		return (float) MapUtils.getTileNumberY(getZoom(), latitude);
	}
	
	/**
	 * @return y tile based on (int) zoom
	 */
	public float getEllipticYTile() {
		return (float) MapUtils.getTileEllipsoidNumberY(getZoom(), latitude);
	}
	
	public void setZoom(float zoom) {
		if (mainLayer != null && zoom <= mainLayer.getMaximumShownMapZoom() && zoom >= mainLayer.getMinimumShownMapZoom()) {
			animatedDraggingThread.stopAnimating();
			this.zoom = zoom;
			refreshMap();
		}
	}

	

	public void setRotate(float rotate) {
		float diff = MapUtils.unifyRotationDiff(rotate, this.rotate);
		if (Math.abs(diff) > 5) { // check smallest rotation
			animatedDraggingThread.startRotate(rotate);
		}
	}

	public boolean isShowMapPosition() {
		return showMapPosition;
	}

	public void setShowMapPosition(boolean showMapPosition) {
		this.showMapPosition = showMapPosition;
	}

	public float getRotate() {
		return rotate;
	}


	public void setLatLon(double latitude, double longitude) {
		animatedDraggingThread.stopAnimating();
		this.latitude = latitude;
		this.longitude = longitude;
		refreshMap();
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public int getZoom() {
		return (int) zoom;
	}

	public boolean isZooming(){
		return zoom != getZoom();
	}
	
	public void setMapLocationListener(IMapLocationListener l) {
		locationListener = l;
	}

	/**
	 * Adds listener to control when map is dragging
	 */
	public IMapLocationListener setMapLocationListener() {
		return locationListener;
	}

	// ////////////////////////////// DRAWING MAP PART /////////////////////////////////////////////

	protected void drawEmptyTile(Canvas cvs, float x, float y, float ftileSize, boolean nightMode) {
		float tileDiv = (ftileSize / emptyTileDivisor);
		for (int k1 = 0; k1 < emptyTileDivisor; k1++) {
			for (int k2 = 0; k2 < emptyTileDivisor; k2++) {
				float xk = x + tileDiv * k1;
				float yk = y + tileDiv * k2;
				if ((k1 + k2) % 2 == 0) {
					cvs.drawRect(xk, yk, xk + tileDiv, yk + tileDiv, paintGrayFill);
				} else {
					cvs.drawRect(xk, yk, xk + tileDiv, yk + tileDiv, nightMode ? paintBlackFill : paintWhiteFill);
				}
			}
		}
	}
	
	public BaseMapLayer getMainLayer() {
		return mainLayer;
	}
	
	public void setMainLayer(BaseMapLayer mainLayer) {
		this.mainLayer = mainLayer;
		if (mainLayer.getMaximumShownMapZoom() < this.zoom) {
			zoom = mainLayer.getMaximumShownMapZoom();
		}
		if (mainLayer.getMinimumShownMapZoom() > this.zoom) {
			zoom = mainLayer.getMinimumShownMapZoom();
		}
		refreshMap();
	}

	public int getCenterPointX() {
		return getWidth() / 2;
	}

	public int getCenterPointY() {
		if (mapPosition == OsmandSettings.BOTTOM_CONSTANT) {
			return 3 * getHeight() / 4;
		}
		return getHeight() / 2;
	}

	public void setMapPosition(int type) {
		this.mapPosition = type;
	}


	public void calculateTileRectangle(Rect pixRect, float cx, float cy, float ctilex, float ctiley, RectF tileRect) {
		float x1 = calcDiffTileX(pixRect.left - cx, pixRect.top - cy);
		float x2 = calcDiffTileX(pixRect.left - cx, pixRect.bottom - cy);
		float x3 = calcDiffTileX(pixRect.right - cx, pixRect.top - cy);
		float x4 = calcDiffTileX(pixRect.right - cx, pixRect.bottom - cy);
		float y1 = calcDiffTileY(pixRect.left - cx, pixRect.top - cy);
		float y2 = calcDiffTileY(pixRect.left - cx, pixRect.bottom - cy);
		float y3 = calcDiffTileY(pixRect.right - cx, pixRect.top - cy);
		float y4 = calcDiffTileY(pixRect.right - cx, pixRect.bottom - cy);
		float l = Math.min(Math.min(x1, x2), Math.min(x3, x4)) + ctilex;
		float r = Math.max(Math.max(x1, x2), Math.max(x3, x4)) + ctilex;
		float t = Math.min(Math.min(y1, y2), Math.min(y3, y4)) + ctiley;
		float b = Math.max(Math.max(y1, y2), Math.max(y3, y4)) + ctiley;
		tileRect.set(l, t, r, b);
	}

	public void calculatePixelRectangle(Rect pixelRect, float cx, float cy, float ctilex, float ctiley, RectF tileRect) {
		float x1 = calcDiffPixelX(tileRect.left - ctilex, tileRect.top - ctiley);
		float x2 = calcDiffPixelX(tileRect.left - ctilex, tileRect.bottom - ctiley);
		float x3 = calcDiffPixelX(tileRect.right - ctilex, tileRect.top - ctiley);
		float x4 = calcDiffPixelX(tileRect.right - ctilex, tileRect.bottom - ctiley);
		float y1 = calcDiffPixelY(tileRect.left - ctilex, tileRect.top - ctiley);
		float y2 = calcDiffPixelY(tileRect.left - ctilex, tileRect.bottom - ctiley);
		float y3 = calcDiffPixelY(tileRect.right - ctilex, tileRect.top - ctiley);
		float y4 = calcDiffPixelY(tileRect.right - ctilex, tileRect.bottom - ctiley);
		int l = Math.round(Math.min(Math.min(x1, x2), Math.min(x3, x4)) + cx);
		int r = Math.round(Math.max(Math.max(x1, x2), Math.max(x3, x4)) + cx);
		int t = Math.round(Math.min(Math.min(y1, y2), Math.min(y3, y4)) + cy);
		int b = Math.round(Math.max(Math.max(y1, y2), Math.max(y3, y4)) + cy);
		pixelRect.set(l, t, r, b);
	}

	// used only to save space & reuse
	protected RectF tilesRect = new RectF();
	protected RectF latlonRect = new RectF();
	protected Rect boundsRect = new Rect();
	protected RectF bitmapToDraw = new RectF();
	protected Rect bitmapToZoom = new Rect();
	protected OsmandSettings settings = null;
	
	public OsmandSettings getSettings(){
		if(settings == null){
			settings = OsmandSettings.getOsmandSettings(getContext());
		}
		return settings;
	}

	private void refreshMapInternal() {
		handler.removeMessages(1);
		
//		 long time = System.currentTimeMillis();

		boolean useInternet = getSettings().USE_INTERNET_TO_DOWNLOAD_TILES.get();
		if (useInternet) {
			MapTileDownloader.getInstance().refuseAllPreviousRequests();
		}
		
//		SurfaceHolder holder = getHolder();
//		synchronized (holder) {
			int nzoom = getZoom();
			float tileX = (float) MapUtils.getTileNumberX(nzoom, longitude);
			float tileY = (float) MapUtils.getTileNumberY(nzoom, latitude);
			float w = getCenterPointX();
			float h = getCenterPointY();
//			Canvas canvas = holder.lockCanvas();
//			if (canvas != null) {
				boolean nightMode = false;
				if (application != null) {
					Boolean dayNightRenderer = application.getDaynightHelper().getDayNightRenderer();
					if (dayNightRenderer != null) {
						nightMode = !dayNightRenderer.booleanValue();
					}
				}
//				try {
					boundsRect.set(0, 0, getWidth(), getHeight());
					calculateTileRectangle(boundsRect, w, h, tileX, tileY, tilesRect);
					latlonRect.top = (float) MapUtils.getLatitudeFromTile(nzoom, tilesRect.top);
					latlonRect.left = (float) MapUtils.getLongitudeFromTile(nzoom, tilesRect.left);
					latlonRect.bottom = (float) MapUtils.getLatitudeFromTile(nzoom, tilesRect.bottom);
					latlonRect.right = (float) MapUtils.getLongitudeFromTile(nzoom, tilesRect.right);
				
					renderer.setNightMode(nightMode);
////					if(nightMode){
////						canvas.drawARGB(255, 100, 100, 100);
////					} else {
////						canvas.drawARGB(255, 225, 225, 225);
////					}
//					// TODO map
////					float ftileSize = getTileSize();
////					int left = (int) FloatMath.floor(tilesRect.left);
////					int top = (int) FloatMath.floor(tilesRect.top);
////					int width = (int) FloatMath.ceil(tilesRect.right - left);
////					int height = (int) FloatMath.ceil(tilesRect.bottom - top);
////					for (int i = 0; i < width; i++) {
////						for (int j = 0; j < height; j++) {
////							float x1 = (i + left - tileX) * ftileSize + w;
////							float y1 = (j + top - tileY) * ftileSize + h;
////							drawEmptyTile(canvas, x1, y1, ftileSize, nightMode);
////						}
////					}
//					requestRender();
//					//drawOverMap(canvas, latlonRect, tilesRect, nightMode);
//					
//					log.info("Draw with layers " + (System.currentTimeMillis() - time));
//				} finally {
//					holder.unlockCanvasAndPost(canvas);
//				}
//			}
//		}
		requestRender();
	}

	private void drawOverMap(GL10 gl, RectF latlonRect, RectF tilesRect, boolean nightMode) {
//		int w = getCenterPointX();
//		int h = getCenterPointY();

		// long prev = System.currentTimeMillis();

		for (int i = 0; i < layers.size(); i++) {
			try {
				
				OsmandMapLayer layer = layers.get(i);
//				canvas.save();
				// rotate if needed
//				if (!layer.drawInScreenPixels()) {
//					canvas.rotate(rotate, w, h);
//				}
				layer.onDraw(gl, latlonRect, tilesRect, nightMode);
//				canvas.restore();
			} catch (IndexOutOfBoundsException e) {
				// skip it
			}
			// long time = System.currentTimeMillis();
			// log.debug("Layer time " + (time - prev) + " " + zOrders.get(layers.get(i)));
			// prev = time;
		}
//		canvas.restore();
//		if (showMapPosition) {
//			canvas.drawCircle(w, h, 3 * dm.density, paintCenter);
//			canvas.drawCircle(w, h, 7 * dm.density, paintCenter);
//		}
	}

	public boolean mapIsRefreshing() {
		return handler.hasMessages(1);
	}

	public boolean mapIsAnimating() {
		return animatedDraggingThread != null && animatedDraggingThread.isAnimating();
	}

	// this method could be called in non UI thread
	public void refreshMap() {
		if (!handler.hasMessages(1)) {
			Message msg = Message.obtain(handler, new Runnable() {
				@Override
				public void run() {
					refreshMapInternal();
				}
			});
			msg.what = 1;
			handler.sendMessageDelayed(msg, 20);
		}
	}

	public void tileDownloaded(DownloadRequest request) {
		// force to refresh map because image can be loaded from different threads
		// and threads can block each other especially for sqlite images when they
		// are inserting into db they block main thread
		refreshMap();
	}

	// ///////////////////////////////// DRAGGING PART ///////////////////////////////////////
	public float calcDiffTileY(float dx, float dy) {
		return (-rotateSin * dx + rotateCos * dy) / getTileSize();
	}

	public float calcDiffTileX(float dx, float dy) {
		return (rotateCos * dx + rotateSin * dy) / getTileSize();
	}

	public float calcDiffPixelY(float dTileX, float dTileY) {
		return (rotateSin * dTileX + rotateCos * dTileY) * getTileSize();
	}

	public float calcDiffPixelX(float dTileX, float dTileY) {
		return (rotateCos * dTileX - rotateSin * dTileY) * getTileSize();
	}

	/**
	 * These methods do not consider rotating
	 */
	public int getMapXForPoint(double longitude) {
		double tileX = MapUtils.getTileNumberX(getZoom(), longitude);
		return (int) ((tileX - getXTile()) * getTileSize() + getCenterPointX());
	}

	public int getMapYForPoint(double latitude) {
		double tileY = MapUtils.getTileNumberY(getZoom(), latitude);
		return (int) ((tileY - getYTile()) * getTileSize() + getCenterPointY());
	}

	public int getRotatedMapXForPoint(double latitude, double longitude) {
		int cx = getCenterPointX();
		double xTile = MapUtils.getTileNumberX(getZoom(), longitude);
		double yTile = MapUtils.getTileNumberY(getZoom(), latitude);
		return (int) (calcDiffPixelX((float) (xTile - getXTile()), (float) (yTile - getYTile())) + cx);
	}

	public int getRotatedMapYForPoint(double latitude, double longitude) {
		int cy = getCenterPointY();
		double xTile = MapUtils.getTileNumberX(getZoom(), longitude);
		double yTile = MapUtils.getTileNumberY(getZoom(), latitude);
		return (int) (calcDiffPixelY((float) (xTile - getXTile()), (float) (yTile - getYTile())) + cy);
	}

	public boolean isPointOnTheRotatedMap(double latitude, double longitude) {
		int cx = getCenterPointX();
		int cy = getCenterPointY();
		double xTile = MapUtils.getTileNumberX(getZoom(), longitude);
		double yTile = MapUtils.getTileNumberY(getZoom(), latitude);
		int newX = (int) (calcDiffPixelX((float) (xTile - getXTile()), (float) (yTile - getYTile())) + cx);
		int newY = (int) (calcDiffPixelY((float) (xTile - getXTile()), (float) (yTile - getYTile())) + cy);
		if (newX >= 0 && newX <= getWidth() && newY >= 0 && newY <= getHeight()) {
			return true;
		}
		return false;
	}

	protected void dragToAnimate(float fromX, float fromY, float toX, float toY, boolean notify) {
		float dx = (fromX - toX);
		float dy = (fromY - toY);
		moveTo(dx, dy);
		if (locationListener != null && notify) {
			locationListener.locationChanged(latitude, longitude, this);
		}
	}

	protected void rotateToAnimate(float rotate) {
		this.rotate = MapUtils.unifyRotationTo360(rotate);
		float rotateRad = (float) Math.toRadians(rotate);
		this.rotateCos = FloatMath.cos(rotateRad);
		this.rotateSin = FloatMath.sin(rotateRad);
		refreshMap();
	}
	
	protected void setLatLonAnimate(double latitude, double longitude, boolean notify) {
		this.latitude = latitude;
		this.longitude = longitude;
		refreshMap();
		if (locationListener != null && notify) {
			locationListener.locationChanged(latitude, longitude, this);
		}
	}
	
	// for internal usage
	protected void zoomToAnimate(float zoom, boolean notify) {
		if (mainLayer != null && mainLayer.getMaximumShownMapZoom() >= zoom && mainLayer.getMinimumShownMapZoom() <= zoom) {
			this.zoom = zoom;
			refreshMap();
			if (notify && locationListener != null) {
				locationListener.locationChanged(latitude, longitude, this);
			}
		}
	}

	public void moveTo(float dx, float dy) {
		float fy = calcDiffTileY(dx, dy);
		float fx = calcDiffTileX(dx, dy);

		this.latitude = MapUtils.getLatitudeFromTile(getZoom(), getYTile() + fy);
		this.longitude = MapUtils.getLongitudeFromTile(getZoom(), getXTile() + fx);
		refreshMap();
		// do not notify here listener

	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			animatedDraggingThread.stopAnimating();
		}
		if (!multiTouchSupport.onTouchEvent(event)) {
			/* return */gestureDetector.onTouchEvent(event);
		}
		return true;
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		if (trackBallDelegate != null) {
			trackBallDelegate.onTrackBallEvent(event);
		}
		return super.onTrackballEvent(event);
	}

	public void setTrackBallDelegate(OnTrackBallListener trackBallDelegate) {
		this.trackBallDelegate = trackBallDelegate;
	}

	public void setOnLongClickListener(OnLongClickListener l) {
		this.onLongClickListener = l;
	}

	public void setOnClickListener(OnClickListener l) {
		this.onClickListener = l;
	}


		public LatLon getLatLonFromScreenPoint(float x, float y) {
		float dx = x - getCenterPointX();
		float dy = y - getCenterPointY();
		float fy = calcDiffTileY(dx, dy);
		float fx = calcDiffTileX(dx, dy);
		double latitude = MapUtils.getLatitudeFromTile(getZoom(), getYTile() + fy);
		double longitude = MapUtils.getLongitudeFromTile(getZoom(), getXTile() + fx);
		return new LatLon(latitude, longitude);
	}

	

	
	public AnimateDraggingMapThread getAnimatedDraggingThread() {
		return animatedDraggingThread;
	}

	
	private class MapTileViewMultiTouchZoomListener implements MultiTouchZoomListener {
		private float initialMultiTouchZoom;
		private PointF initialMultiTouchCenterPoint;
		private LatLon initialMultiTouchLocation;
		
		@Override
		public void onZoomEnded(float distance, float relativeToStart) {
			float dz = (float) (Math.log(relativeToStart) / Math.log(2) * 1.5);
			float calcZoom = initialMultiTouchZoom + dz;
			setZoom(Math.round(calcZoom));
			zoomPositionChanged(getZoom());
		}

		@Override
		public void onZoomStarted(float distance, PointF centerPoint) {
			initialMultiTouchCenterPoint = centerPoint;
			initialMultiTouchLocation = getLatLonFromScreenPoint(centerPoint.x, centerPoint.y);
			initialMultiTouchZoom = zoom;
		}

		@Override
		public void onZooming(float distance, float relativeToStart) {
			float dz = (float) (Math.log(relativeToStart) / Math.log(2) * 1.5);
			float calcZoom = initialMultiTouchZoom + dz;
			if (Math.abs(calcZoom - zoom) > 0.05) {
				setZoom(calcZoom);
				zoomPositionChanged(calcZoom);
			}
		}
		
		private void zoomPositionChanged(float calcZoom) {
			float dx = initialMultiTouchCenterPoint.x - getCenterPointX();
			float dy = initialMultiTouchCenterPoint.y - getCenterPointY();
			float ex = calcDiffTileX(dx, dy);
			float ey = calcDiffTileY(dx, dy);
			int z = (int)calcZoom;
			double tx = MapUtils.getTileNumberX(z, initialMultiTouchLocation.getLongitude());
			double ty = MapUtils.getTileNumberY(z, initialMultiTouchLocation.getLatitude());
			double lat = MapUtils.getLatitudeFromTile(z, ty - ey);
			double lon = MapUtils.getLongitudeFromTile(z, tx - ex);
			setLatLon(lat, lon);
		}

	}

	private class MapTileViewOnGestureListener implements OnGestureListener {
		@Override
		public boolean onDown(MotionEvent e) {
			// enable double tap animation
			// animatedDraggingThread.stopAnimating();
			return false;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//			if (Math.abs(e1.getX() - e2.getX()) + Math.abs(e1.getY() - e2.getY()) > 50 * dm.density) {
				animatedDraggingThread.startDragging(velocityX, velocityY, 
						e1.getX(), e1.getY(), e2.getX(), e2.getY(), true);
//			} else {
//				onScroll(e1, e2, e1.getX() - e2.getX(), e1.getY() - e2.getY());
//			}
			return true;
		}

		@Override
		public void onLongPress(MotionEvent e) {
			if (multiTouchSupport.isInZoomMode()) {
				return;
			}
			if (log.isDebugEnabled()) {
				log.debug("On long click event " + e.getX() + " " + e.getY()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			PointF point = new PointF(e.getX(), e.getY());
			for (int i = layers.size() - 1; i >= 0; i--) {
				if (layers.get(i).onLongPressEvent(point)) {
					return;
				}
			}
			if (onLongClickListener != null && onLongClickListener.onLongPressEvent(point)) {
				return;
			}
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			dragToAnimate(e2.getX() + distanceX, e2.getY() + distanceY, e2.getX(), e2.getY(), true);
			return true;
		}

		@Override
		public void onShowPress(MotionEvent e) {
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			PointF point = new PointF(e.getX(), e.getY());
			if (log.isDebugEnabled()) {
				log.debug("On click event " + point.x + " " + point.y); //$NON-NLS-1$ //$NON-NLS-2$
			}
			for (int i = layers.size() - 1; i >= 0; i--) {
				if (layers.get(i).onTouchEvent(point)) {
					return true;
				}
			}
			if (onClickListener != null && onClickListener.onPressEvent(point)) {
				return true;
			}
			return false;
		}
	}


	private class MapTileViewOnDoubleTapListener implements OnDoubleTapListener {
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			LatLon l = getLatLonFromScreenPoint(e.getX(), e.getY());
			getAnimatedDraggingThread().startMoving(l.getLatitude(), l.getLongitude(), getZoom() + 1, true);
			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			return false;
		}
	}

}
