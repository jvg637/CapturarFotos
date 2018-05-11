package org.example.previewbasico;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
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
import android.view.SubMenu;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_EXTERNAL;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

public class MainActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {
    private final String TAG = "PreviewBasico";
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
    private int indiceResolucion;
    private SubMenu listadoResoluciones;

    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "En onCreate !!!!");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureview = (TextureView) findViewById(R.id.textureView);
        assert textureview != null;

        // Inicializa menu contextual
        popup = new PopupMenu(this, findViewById(R.id.content_main));
        popup.inflate(R.menu.menu);
        popup.setOnMenuItemClickListener(this);
        listadoCamaras = popup.getMenu().findItem(R.id.cambiarCamara).getSubMenu();
        listadoResoluciones = popup.getMenu().findItem(R.id.cambiarResolucion).getSubMenu();


        if (savedInstanceState != null) {
            indiceCamara = savedInstanceState.getInt(STATE_CAMERA_INDEX, 0);
            indiceCamara = savedInstanceState.getInt(STATE_CAMERA_INDEX, 0);
        } else {
            indiceCamara = indiceResolucion = 0;
        }

        btnCapture = (Button) findViewById(R.id.btnCaptura);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
//                tomarImagen();
            }
        });

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
// Save the current camera index.
        savedInstanceState.putInt(STATE_CAMERA_INDEX, indiceCamara);
        super.onSaveInstanceState(savedInstanceState);
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                                              int height) {
            //open your camera here
            Log.i(TAG, "Abriendo camara desde onSurfaceTextureAvailable");
            abrirCamara();
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int
                width, int height) {
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };


    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        solicitarPermisos();

    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void abrirCamara() {
        Log.i(TAG, "En abrir Camara");
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            mCameraId = manager.getCameraIdList()[indiceCamara];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size tamanos[] = map.getOutputSizes(SurfaceTexture.class);
            listadoResoluciones.clear();
            int cont = 0;
            for (Size tam : tamanos) {
                listadoResoluciones.add(1, 1, cont++, "Resolución: " + tam.toString());
            }
            dimensionesPreview = tamanos[indiceResolucion];
//            dimensionesPreview=tamanos[0];
            //El primer tamaño posible. Normalmente el mayor
            dimensionesJPEG = map.getOutputSizes(ImageFormat.JPEG)[0];

            Log.i(TAG, "Dimensiones Imagen =" +
                    String.valueOf(dimensionesPreview));
            manager.openCamera(mCameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
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

    protected void comenzarPreview() {
        if (null == mCameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                    null, mBackgroundHandler);
            Log.v(TAG, "*****setRepeatingRequest");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static final int SOLICITUD_PERMISOS = 0;

    private void solicitarPermisos() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, SOLICITUD_PERMISOS);
        } else {
            inicializaAplicacion();
        }
    }

    private void inicializaAplicacion() {
        // permission denied, boo! Disable the
        // functionality that depends on this permission.
        enumeraCamaras();
        startBackgroundThread();
        Log.i(TAG, "Setting textureListener a textureview");
        textureview.setSurfaceTextureListener(textureListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case SOLICITUD_PERMISOS: {
                // If request is cancelled, the result arrays are empty.

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {/* permission was granted, yay! Do the contacts-related task you need to do.*/
                    Toast.makeText(this, "Se deben aceptar todos los permisos para inicializar la aplicación", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    inicializaAplicacion();
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
                break;
            default:
                String titulo = item.getTitle().toString();
                if (titulo.startsWith("Camara: ")) {
                    indiceCamara = item.getOrder();
                    mCameraDevice.close();
                    abrirCamara();
                } else {

                    if (titulo.startsWith("Resolución: ")) {
                        indiceResolucion = item.getOrder();
                        mCameraDevice.close();
                        abrirCamara();
                    }
                }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        clickMenu(item);
        return true;
    }

    public void mostrarMenuContextual(View view) {
        popup.show();
    }

    String[] cameras = null;

    private void enumeraCamaras() {
        CameraManager manager =
                (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try

        {

            cameras = manager.getCameraIdList();
            int cont = 0;
            listadoCamaras.clear();
            for (String id : cameras) {
                CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(id);
                List<CameraCharacteristics.Key<?>> keys =
                        characteristics.getKeys();
                for (CameraCharacteristics.Key<?> key : keys) {
                    String nombrecaracteristica = key.getName();
                    Log.e(TAG, "Cámara :" + id + ":" + nombrecaracteristica);
                }
                printNivelHardware(id, characteristics);
                printCaracteristicasPrincipales(id, characteristics, cont);
                printModosEnfoque(id, characteristics);
                printModosExposicion(id, characteristics);
                printResolucionesCamara(id, characteristics);
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


        listadoCamaras.add(1, 1, cont, "Camara: " + lf);
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
    protected void onPause() {

        stopBackgroundThread();
        super.onPause();

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
}}