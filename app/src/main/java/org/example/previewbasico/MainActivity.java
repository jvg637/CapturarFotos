package org.example.previewbasico;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.util.Size;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_EXTERNAL;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_UP;

public class MainActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {
    private final String TAG = "Lupa";
    private TextureView textureview;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Size dimensionesPreview;
    //* Thread adicional para ejecutar tareas que no bloqueen Int usuario.
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private PopupMenu popup; /* menu contextual*/
    private SubMenu listadoCamaras;
    private int indiceCamara;
    private static final String STATE_CAMERA_INDEX = "cameraIndex";
    private static final String PREVIEW_RESOLUTION_INDEX = "previewResoluctionIndex";
    private static final String CAPTURE_RESOLUTION_INDEX = "captureResoluctionIndex";
    private int indiceResolucionPreview;
    private int indiceResolucionCaptura;
    private SubMenu listadoResolucionesPreview;
    private SubMenu listadoResolucionesCaptura;
    private TextView txtZoom;
    private TextView txtResolucion;

    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "En onCreate !!!!");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureview = (TextureView) findViewById(R.id.textureView);
        assert textureview != null;

        txtZoom = findViewById(R.id.nivelZoomm);
        txtResolucion = findViewById(R.id.resolucion);
        // Inicializa menu contextual
        popup = new PopupMenu(this, findViewById(R.id.content_main));
        popup.inflate(R.menu.menu);
        popup.setOnMenuItemClickListener(this);

        listadoCamaras = popup.getMenu().findItem(R.id.cambiarCamara).getSubMenu();
        listadoResolucionesPreview = popup.getMenu().findItem(R.id.cambiarResolucionPreview).getSubMenu();
        listadoResolucionesCaptura = popup.getMenu().findItem(R.id.cambiarResolucionCaptura).getSubMenu();

        if (savedInstanceState != null) {
            indiceCamara = savedInstanceState.getInt(STATE_CAMERA_INDEX, 0);
            indiceResolucionPreview = savedInstanceState.getInt(PREVIEW_RESOLUTION_INDEX, 0);
            indiceResolucionCaptura = savedInstanceState.getInt(CAPTURE_RESOLUTION_INDEX, 0);
        } else {
            indiceCamara = indiceResolucionPreview = indiceResolucionCaptura = 0;
        }

        textureview.setOnTouchListener(handleTouch);

    }

    private void guardar(byte[] bytes) {


        final File rutaPictures =
                new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES).toString());
        String strFichero = rutaPictures + "/" +
                (listadoCamaras.getItem(indiceCamara).getTitle().toString().split(":")[1]) + System.currentTimeMillis() + ".jpg";

        if (!rutaPictures.exists()) {
            rutaPictures.mkdir();
        }


        final File fichero = new File(strFichero);

        OutputStream output = null;

        try {
            output = new FileOutputStream(fichero);
            output.write(bytes);
            output.close();
            Toast.makeText(MainActivity.this, strFichero + " saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error escribiendo fichero: " + fichero);
            e.printStackTrace();
        } finally {

            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
// Save the current camera index.
        savedInstanceState.putInt(STATE_CAMERA_INDEX, indiceCamara);
        savedInstanceState.putInt(PREVIEW_RESOLUTION_INDEX, indiceResolucionPreview);
        savedInstanceState.putInt(CAPTURE_RESOLUTION_INDEX, indiceResolucionCaptura);
        super.onSaveInstanceState(savedInstanceState);
    }


    protected void onStart() {
        super.onStart();
        Log.e(TAG, "onStart");
        solicitarPermisos();

    }

    protected void startBackgroundThread() {
        textureview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                                                  int height) {
                //open your camera here
                Log.i(TAG, "Abriendo camara desde onSurfaceTextureAvailable");
                abrirCamara();
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int
                    width, int height) {
//                Log.i(TAG, "onSurfaceTextureSizeChanged");
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
//                Log.i(TAG, "onSurfaceTextureDestroyed");
                return false;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//                Log.i(TAG, "onSurfaceTonSurfaceTextureUpdatedextureDestroyed");

            }

        });

        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        if (mBackgroundThread != null)
            mBackgroundThread.quitSafely();
        try {
            if (mBackgroundThread != null)
                mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        if (textureview != null)
//            textureview.setSurfaceTextureListener(null);
//        if (mCameraDevice!=null)
//            mCameraDevice.close();

    }

    Size tamanos[];
    Size tamanosCapture[];

    private void abrirCamara() {
        Log.i(TAG, "En abrir Camara");
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            enumeraCamaras(manager);
            mCameraId = manager.getCameraIdList()[indiceCamara];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            maxzoom = characteristics.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            Rect m = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            pixels_anchura_sensor = m.width();
            pixels_altura_sensor = m.height();
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            tamanos = map.getOutputSizes(SurfaceTexture.class);
            listadoResolucionesPreview.clear();
            int cont = 0;
            for (Size tam : tamanos) {
                listadoResolucionesPreview.add(1, 1, cont++, "Resolución: " + tam.toString());
            }
            dimensionesPreview = tamanos[indiceResolucionPreview];

//            dimensionesPreview=tamanos[0];
            //El primer tamaño posible. Normalmente el mayor
            tamanosCapture = map.getOutputSizes(ImageFormat.JPEG);

            listadoResolucionesCaptura.clear();
            cont = 0;
            for (Size tam : tamanosCapture) {
                listadoResolucionesCaptura.add(1, 1, cont++, "Resolución Captura: " + tam.toString());
            }
            dimensionesJPEG = tamanosCapture[indiceResolucionCaptura];

            zoom_level = maxzoom / 2;
            int width = (int) (pixels_anchura_sensor / zoom_level);
            int height = (int) (pixels_altura_sensor / zoom_level);
            int startx = (pixels_anchura_sensor - width) / 2;
            int starty = (pixels_altura_sensor - height) / 2;
            Rect zonaActiva = new Rect(startx, starty, startx + width,
                    starty + height);
            zoom = zonaActiva;

            escribeDatosCamara();


            Log.i(TAG, "Dimensiones Imagen Preview =" + String.valueOf(dimensionesPreview) +
                    "Dimensiones Imagen Captura =" + String.valueOf(dimensionesJPEG) +
                    "Dimensiones Sensor: " + m.toString() + "Maxzoom="
                    + String.valueOf(maxzoom));
            manager.openCamera(mCameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void escribeDatosCamara() {
        if (dimensionesPreview != null && dimensionesPreview != null) {
            txtZoom.setText("Zoom: " + String.format("%2.2f", (float) zoom_level) + "/" + String.format("%2.2f", maxzoom));
            txtResolucion.setText(dimensionesPreview.toString() + "-" + dimensionesJPEG);
            txtZoom.invalidate();
            txtResolucion.invalidate();
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        public void onOpened(CameraDevice camera) {
            Log.e(TAG, "onOpened");
            mCameraDevice = camera;
            crearPreviewCamara();
        }

        public void onDisconnected(CameraDevice camera) {
            camera.close();
        }

        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    public float separacion = 0;
    public double zoom_level = 1.0;
    private int pixels_anchura_sensor;
    private int pixels_altura_sensor;
    float maxzoom;
    Rect zoom;

    private void crearPreviewCamara() {
        try {
            SurfaceTexture texture = textureview.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(dimensionesPreview.getWidth(),
                    dimensionesPreview.getHeight());
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                    CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            CameraCaptureSession.StateCallback statecallback =
                    new CameraCaptureSession.StateCallback() {
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            Log.i(TAG, "Sesión de captura configurada para preview");
                            //The camera is closed
                            if (null == mCameraDevice) {
                                return;
                            }
                            // Cuando la sesion este lista empezamos a visualizer imags .
                            mCaptureSession = cameraCaptureSession;
                            comenzarPreview();
                        }

                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "Configuration change failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    };
            mCameraDevice.createCaptureSession(Arrays.asList(surface),
                    statecallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    protected void comenzarPreview() {
//        if (null == mCameraDevice) {
//            Log.e(TAG, "updatePreview error, return");
//        }
//        try {
//            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
//                    null, mBackgroundHandler);
//            Log.v(TAG, "*****setRepeatingRequest");
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }

    private void comenzarPreview() {
        configurarImageReader();
        try {
            SurfaceTexture texture = textureview.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(dimensionesPreview.getWidth(),
                    dimensionesPreview.getHeight());
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(
                    Arrays.asList(surface, mImageReader.getSurface()),
                    cameraCaptureSessionStatecallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configurarImageReader() {
        try {
            dimensionesJPEG = tamanosCapture[indiceResolucionCaptura];
            mImageReader = ImageReader.newInstance(dimensionesJPEG.getWidth(),
                    dimensionesJPEG.getHeight(), ImageFormat.JPEG, 1);
            mImageReader.setOnImageAvailableListener(readerListener,
                    mBackgroundHandler);
            mJPEGRequestBuilder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            mJPEGRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            //Decirle donde se dejarán las imágenes
            mJPEGRequestBuilder.addTarget(mImageReader.getSurface());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    CameraCaptureSession.StateCallback cameraCaptureSessionStatecallback =
            new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCaptureSession = cameraCaptureSession;
                    updatePreview();
                }

                public void onConfigureFailed(CameraCaptureSession camCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuracion fallida", Toast.LENGTH_SHORT).show();
                }
            };

    protected void updatePreview() {
        if (null == mCameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                CameraMetadata.CONTROL_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON);
        mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                    null, mBackgroundHandler);
            Log.i(TAG, "*****setRepeatingRequest. Captura preview arrancada");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void tomarImagen() {
        try {
            mJPEGRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                    CameraMetadata.CONTROL_MODE_AUTO);
            mJPEGRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mJPEGRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
            mJPEGRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest
                        request, TotalCaptureResult result) {
                    comenzarPreview();
                }
            };
            mCaptureSession.capture(mJPEGRequestBuilder.build(),
                    CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    ImageReader.OnImageAvailableListener readerListener =
            new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader reader) {
                    Image imagen = null;

                    imagen = reader.acquireLatestImage();
                    //Aquí se podría guardar o procesar la imagen
                    ByteBuffer buffer = imagen.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    guardar(bytes);

                }
            };

    private static final int SOLICITUD_PERMISOS = 1234;

    private void solicitarPermisos() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, SOLICITUD_PERMISOS);
        } else {
            inicializaAplicacion();
        }
    }

    private void inicializaAplicacion() {
        // permission denied, boo! Disable the
        // functionality that depends on this permission.
        startBackgroundThread();
        Log.i(TAG, "Setting textureListener a textureview");

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case SOLICITUD_PERMISOS: {
                // If request is cancelled, the result arrays are empty.

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                        ||
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Se deben aceptar todos los permisos para inicializar la aplicación", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    inicializaAplicacion();
                    recreate();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }

    }


    private void clickMenu(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.cambiarCamara:
                Log.d("OPTION", "cambiarCamara Pulsado");
                break;
            case R.id.guardar_imagenes:
                Log.d("OPTION", "guardar_imagenes Pulsado");
                tomarImagen();
                break;
            default:
                String titulo = item.getTitle().toString();
                if (titulo.startsWith("id: ")) {
                    indiceCamara = item.getOrder();
                    if (mCameraDevice != null)
                        mCameraDevice.close();
                    indiceResolucionCaptura = 0;
                    indiceResolucionPreview = 0;
//                    stopBackgroundThread();
//                    startBackgroundThread();
//                    abrirCamara();
                    recreate();

                } else {
                    if (titulo.startsWith("Resolución: ")) {
                        indiceResolucionPreview = item.getOrder();
                        dimensionesPreview = tamanos[indiceResolucionPreview];
                        escribeDatosCamara();
                        if (mCameraDevice != null)
                            mCameraDevice.close();
//                        stopBackgroundThread();
//                        startBackgroundThread();
////                        abrirCamara();
                        recreate();


                    } else {
                        if (titulo.startsWith("Resolución Captura: ")) {
                            indiceResolucionCaptura = item.getOrder();
                            dimensionesJPEG = tamanosCapture[indiceResolucionCaptura];
                            escribeDatosCamara();
                        }
                    }
                }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        clickMenu(item);
        return true;
    }


    String[] cameras = null;

    private void enumeraCamaras(CameraManager manager) {
        try {
            cameras = manager.getCameraIdList();
            int cont = 0;
            listadoCamaras.clear();
            for (String id : cameras) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
//                List<CameraCharacteristics.Key<?>> keys = characteristics.getKeys();
//                for (CameraCharacteristics.Key<?> key : keys) {
//                    String nombrecaracteristica = key.getName();
//                    Log.e(TAG, "Cámara :" + id + ":" + nombrecaracteristica);
//                }
//                printNivelHardware(id, characteristics);
                printCaracteristicasPrincipales(id, characteristics, cont);
//                printModosEnfoque(id, characteristics);
//                printModosExposicion(id, characteristics);
//                printResolucionesCamara(id, characteristics);
                cont++;
            }
        } catch (
                CameraAccessException e)

        {
            Log.e(TAG, "No puedo obtener lista de cámaras");
            e.printStackTrace();
        }
    }

    void printNivelHardware(String cameraId,
                            CameraCharacteristics characteristics) {
        int nivel = characteristics.get(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (nivel ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY)
            Log.i(TAG2, "Camera " + cameraId + ": Nivel Hw Legacy");
        else
            Log.i(TAG2, "Camera " + cameraId + ": Nivel Hw =" +
                    String.valueOf(nivel));
    }

    // Devuelve true si el dispositivo soporta el nivel de hw requerido u otro mejor
    boolean isHardwareLevelSupported(CameraCharacteristics c,
                                     int requiredLevel) {
        int deviceLevel =
                c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel no es LEGACY, se puede usar ordenamiento numérico
        return requiredLevel <= deviceLevel;
    }

    private void printCaracteristicasPrincipales(String cameraId, CameraCharacteristics
            caracteristicas, int cont) {

        int lensfacing = caracteristicas.get(CameraCharacteristics.LENS_FACING);
        String lf = "Desconocida";
        if (lensfacing == LENS_FACING_FRONT)
            lf = "Frontal";
        else if (lensfacing == LENS_FACING_BACK)
            lf = "Trasera";
        else if (lensfacing == LENS_FACING_EXTERNAL)
            lf = "Externa";
        Log.i(TAG2, "Cámara " + cameraId + ": LENS FACING = " + lf);
        int orientation = caracteristicas.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Log.i(TAG2, "Camera " + cameraId + ": SENSOR ORIENTATION = " +
                String.valueOf(orientation));


        listadoCamaras.add(1, 1, cont, "id: " + lf + cameraId);
    }

    private void printModosEnfoque(String cameraId,
                                   CameraCharacteristics characteristics) {
        Log.i(TAG2, "MODOS ENFOQUE");
        int[] afmodes;
        afmodes = characteristics.get(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        for (int m : afmodes) {
            if (m == CaptureRequest.CONTROL_AF_MODE_AUTO)
                Log.i(TAG2, "Camera " + cameraId +

                        ": Auto Enfoque Básico disponible");
            if (m == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                Log.i(TAG2, "Camera " + cameraId +
                        ": Auto Enfoque VIDEO continuo disponible");
            if (m == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                Log.i(TAG2, "Camera " + cameraId +
                        ": Auto Enfoque IMAGEN continuo disponible");
            if (m == CaptureRequest.CONTROL_AF_MODE_OFF) {
                Log.i(TAG2, "Camera " + cameraId + ": Enfoque manual disponible");
                if (isHardwareLevelSupported(characteristics,
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)) {
                    float minimaDistanciaEnfoque = characteristics.get(
                            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                    if (minimaDistanciaEnfoque > 0)
                        Log.i(TAG2, "Camera " + cameraId +
                                ": Distancia Mínima de Enfoque = " +
                                String.valueOf(minimaDistanciaEnfoque));
                    else
                        Log.i(TAG2, "Camera " + cameraId + ": Foco Fijo ");
                }
            }
        }
        if (isMeteringAreaAFSupported(cameraId, characteristics)) {
            Log.i(TAG2, "Camera " + cameraId +
                    ": Regiones de autoenfoque soportadas");
        } else {
            Log.i(TAG2, "Camera " + cameraId +
                    ": Regiones de autoenfoque NO soportadas");
        }
    }

    private boolean isMeteringAreaAFSupported(String cameraId,
                                              CameraCharacteristics characteristics) {
        int numRegiones = characteristics.get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return numRegiones >= 1;
    }

    private void printModosExposicion(String cameraId, CameraCharacteristics
            characteristics) {
        Log.i(TAG2, "MODOS EXPOSICION");
        int[] aemodes;
        aemodes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        for (int m : aemodes) {
            if (m == CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                Log.i(TAG2, "Camera " + cameraId +
                        ": Control Automático Exposición con Flash automático disponible");
            if (m == CaptureRequest.CONTROL_AE_MODE_ON)
                Log.i(TAG2, "Camera " + cameraId +
                        ": Control Automático Exposición con Flash apagado disponible");
            if (m == CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                Log.i(TAG2, "Camera " + cameraId +
                        ": Control Automático Exposición con Flash encendido disponible");
            if (m == CaptureRequest.CONTROL_AE_MODE_OFF)
                Log.i(TAG2, "Camera " + cameraId +
                        ": Ajuste Manual Exposición Disponible");
        }
    }

    void printResolucionesCamara(String cameraId, CameraCharacteristics caracteristicas) {
        StreamConfigurationMap map = caracteristicas.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        assert map != null;
        Size[] dimensiones;
        dimensiones = map.getOutputSizes(SurfaceTexture.class);
        for (Size s : dimensiones) {
            Log.e(TAG, cameraId + " : Resolucion=" + s.toString());
        }
    }

    private static final String TAG2 = " *** CaractPrincipal";

    @Override
    protected void onStop() {
        stopBackgroundThread();

        super.onStop();
    }

    /////////////////////////////////////////////
// CATURAR FOTO
/////////////////////////////////////////////
    private Button btnCapture;
    //    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest.Builder mJPEGRequestBuilder;
    private ImageReader mImageReader;
    //    private Size dimensionesPreview;
    private Size dimensionesJPEG;


    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    int maxPointers = 0;
    private View.OnTouchListener handleTouch = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            // Controla Click para mostrar menu
            int action = (event.getAction() & MotionEvent.ACTION_MASK);
            if (action == ACTION_DOWN || action == ACTION_POINTER_DOWN) maxPointers++;
            if (action == ACTION_UP) {
                if (maxPointers <= 1) popup.show();
                maxPointers = 0;
            }

            float sep_actual;
            if (event.getPointerCount() > 1) {// Multi touch
                sep_actual = getFingerSpacing(event);
                if (separacion != 0) {
                    if (sep_actual > separacion && maxzoom >= zoom_level + 0.1) {
                        zoom_level += 0.1;
                    } else if (sep_actual < separacion && zoom_level > 1.0) {
                        zoom_level -= 0.1;
                    }
                    int width = (int) (pixels_anchura_sensor / zoom_level);
                    int height = (int) (pixels_altura_sensor / zoom_level);
                    int startx = (pixels_anchura_sensor - width) / 2;
                    int starty = (pixels_altura_sensor - height) / 2;
                    Rect zonaActiva = new Rect(startx, starty, startx + width, starty + height);
                    mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zonaActiva);
                    mJPEGRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zonaActiva);
                    zoom = zonaActiva;
                }
                separacion = sep_actual;
            } else {
                separacion = 0;
            }

            escribeDatosCamara();

            try {
                mCaptureSession.setRepeatingRequest(
                        mPreviewRequestBuilder.build(), null, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (NullPointerException ex) {
                ex.printStackTrace();

            } catch (Exception ex) {
                ex.printStackTrace();
            }

            return true;
        }
    };

}