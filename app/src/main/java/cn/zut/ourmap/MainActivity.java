package cn.zut.ourmap;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.MapBaseIndoorMapInfo;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.List;

import cn.zut.ourmap.indoorview.BaseStripAdapter;
import cn.zut.ourmap.indoorview.StripListView;


public class MainActivity extends AppCompatActivity implements SensorEventListener{
    private MapView mMapView;
    private BaiduMap mBaiduMap;

    // 楼层条View
    private StripListView mStripListView;
    private BaseStripAdapter mFloorListAdapter;
    private MapBaseIndoorMapInfo mMapBaseIndoorMapInfo = null;

    //传感器
    private MyLocationConfiguration.LocationMode mCurrentMode;
    private SensorManager mSensorManager;

    private RadioButton rB_NORMAL;
    private RadioButton rB_SATELLITE;
    private RadioButton rB_TRAFFIC;
    private RadioButton rB_HEAT;
    private RadioGroup rg;

    private Double lastX = 0.0;
    private int mCurrentDirection = 0;
    private double mCurrentLat = 0.0;
    private double mCurrentLon = 0.0;
    private float mCurrentAccracy;
    private MyLocationData myLocationData;

    private boolean isFirstLoc = true;
    public LocationClient mLocationClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);


        RelativeLayout layout = new RelativeLayout(this);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View mainview = inflater.inflate(R.layout.maplayout, mMapView);
        layout.addView(mainview);

        // 楼层条View
        mStripListView = new StripListView(this);
        // 楼层条数据适配器
        mFloorListAdapter = new BaseStripAdapter(MainActivity.this);
        layout.addView(mStripListView);
        setContentView(layout);

        SDKInitializer.initialize(getApplicationContext());

        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        }
        if (!permissionList.isEmpty()) {
            String[] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
        }


        mMapView = findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();

        // 获取传感器管理服务
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);



        mCurrentMode = MyLocationConfiguration.LocationMode.FOLLOWING;
        mBaiduMap.setMyLocationConfiguration(new MyLocationConfiguration(mCurrentMode, true, null));
        MapStatus.Builder builder = new MapStatus.Builder();
        builder.overlook(0);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));

        // 为系统的方向传感器注册监听器
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_UI);



        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        mBaiduMap.setMyLocationEnabled(true);
        //打开室内图，默认为关闭状态
        mBaiduMap.setIndoorEnable(true);

        rg = findViewById(R.id.rg);
        rB_NORMAL = findViewById(R.id.rB_NORMAL);
        rB_SATELLITE = findViewById(R.id.rB_SATELLITE);
        rB_TRAFFIC = findViewById(R.id.rB_TRAFFIC);
        rB_HEAT = findViewById(R.id.rB_Heat);
        rg.setOnCheckedChangeListener(new MyRadioButtonListener());


        // 定位初始化
        initLocationOption();
        mLocationClient = new LocationClient(this);
        isFirstLoc = true;



        // 设置室内图模式监听
        mBaiduMap.setOnBaseIndoorMapListener(new BaiduMap.OnBaseIndoorMapListener() {
            @Override
            public void onBaseIndoorMapMode(boolean isIndoorMap, MapBaseIndoorMapInfo mapBaseIndoorMapInfo) {
                if (!isIndoorMap || mapBaseIndoorMapInfo == null) {
                    mStripListView.setVisibility(View.INVISIBLE);
                    return;
                }
                // 设置楼层数据
                mBaiduMap.showMapIndoorPoi(true);
                mFloorListAdapter.setmFloorList(mapBaseIndoorMapInfo.getFloors());
                mStripListView.setVisibility(View.VISIBLE);
                mStripListView.setStripAdapter(mFloorListAdapter);
                mMapBaseIndoorMapInfo = mapBaseIndoorMapInfo;
            }
        });
        mStripListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mMapBaseIndoorMapInfo == null) {
                    return;
                }
                String floor = (String) mFloorListAdapter.getItem(position);
                mBaiduMap.switchBaseIndoorMapFloor(floor, mMapBaseIndoorMapInfo.getID());
                mFloorListAdapter.setSelectedPostion(position);
                mFloorListAdapter.notifyDataSetInvalidated();
            }
        });
    }

    /**
     * 初始化定位参数配置
     */

    private void initLocationOption() {
//定位服务的客户端。宿主程序在客户端声明此类，并调用，目前只支持在主线程中启动
        LocationClient locationClient = new LocationClient(getApplicationContext());
//声明LocationClient类实例并配置定位参数
        LocationClientOption locationOption = new LocationClientOption();
        MyLocationListener myLocationListener = new MyLocationListener();
//注册监听函数
        locationClient.registerLocationListener(myLocationListener);

//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        locationOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
//可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        locationOption.setCoorType("bd09ll");
//可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        locationOption.setScanSpan(1000);
//可选，设置是否需要地址信息，默认不需要
        locationOption.setIsNeedAddress(true);
//可选，设置是否需要地址描述
        locationOption.setIsNeedLocationDescribe(true);
//可选，设置是否需要设备方向结果
        locationOption.setNeedDeviceDirect(true);
//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        locationOption.setLocationNotify(true);
//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        locationOption.setIgnoreKillProcess(true);
//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        locationOption.setIsNeedLocationDescribe(true);
//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        locationOption.setIsNeedLocationPoiList(true);
//可选，默认false，设置是否收集CRASH信息，默认收集
        locationOption.SetIgnoreCacheException(false);
//可选，默认false，设置是否开启Gps定位
        locationOption.setOpenGps(true);
//可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用
        locationOption.setIsNeedAltitude(false);
//设置打开自动回调位置模式，该开关打开后，期间只要定位SDK检测到位置变化就会主动回调给开发者，该模式下开发者无需再关心定位间隔是多少，定位SDK本身发现位置变化就会及时回调给开发者
        locationOption.setOpenAutoNotifyMode();
//设置打开自动回调位置模式，该开关打开后，期间只要定位SDK检测到位置变化就会主动回调给开发者
        locationOption.setOpenAutoNotifyMode(3000, 1, LocationClientOption.LOC_SENSITIVITY_HIGHT);
//需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
        locationClient.setLocOption(locationOption);
//开始定位
        locationClient.start();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        double x = sensorEvent.values[SensorManager.DATA_X];
        if (Math.abs(x - lastX) > 1.0) {
            mCurrentDirection = (int) x;
            myLocationData = new MyLocationData.Builder()
                    .accuracy(mCurrentAccracy)// 设置定位数据的精度信息，单位：米
                    .direction(mCurrentDirection)// 此处设置开发者获取到的方向信息，顺时针0-360
                    .latitude(mCurrentLat)
                    .longitude(mCurrentLon)
                    .build();
            mBaiduMap.setMyLocationData(myLocationData);
        }
        lastX = x;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null)
                return;
            mCurrentLat = location.getLatitude();
            mCurrentLon = location.getLongitude();
            // 构造定位数据
            mBaiduMap.setMyLocationEnabled(true);
            //构造定位数据
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(mCurrentDirection).latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
//            MyLocationData locData = new MyLocationData.Builder()
//                    .accuracy(location.getRadius())
//                    // 此处设置开发者获取到的方向信息，顺时针0-360
//                    .direction(location.getDirection()).latitude(34.755121)
//                    .longitude(113.621725).build();
            // 设置定位数据
            mBaiduMap.setMyLocationData(locData);
  // 第一次定位时，将地图位置移动到当前位置
            if (isFirstLoc) {
                isFirstLoc = false;
                //LatLng xy = new LatLng(34.755121, 113.621725);
                LatLng xy = new LatLng(location.getLatitude(),location.getLongitude());
                MapStatus.Builder builder = new MapStatus.Builder();
                builder.target(xy).zoom(18.0f);
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
            }


        }
    }


    class MyRadioButtonListener implements RadioGroup.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            mBaiduMap.setTrafficEnabled(false);
            mBaiduMap.setBaiduHeatMapEnabled(false);
            // 选中状态改变时被触发
            switch (checkedId) {
                case R.id.rB_NORMAL:
                    //获取地图控件引用普通地图
                    mMapView = findViewById(R.id.bmapView);
                    mBaiduMap = mMapView.getMap();
                    mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
                    break;
                case R.id.rB_SATELLITE:
                    //获取卫星地图
                    mMapView = findViewById(R.id.bmapView);
                    mBaiduMap = mMapView.getMap();
                    mBaiduMap.setMapType(BaiduMap.MAP_TYPE_SATELLITE);
                    break;
                case R.id.rB_TRAFFIC:
                    //开启交通图
                    mMapView = findViewById(R.id.bmapView);
                    mBaiduMap = mMapView.getMap();

                    mBaiduMap.setTrafficEnabled(true);
                    break;
                case R.id.rB_Heat:
                    //开启热力图
                    mMapView = findViewById(R.id.bmapView);
                    mBaiduMap = mMapView.getMap();
                    mBaiduMap.setBaiduHeatMapEnabled(true);
                    break;
            }
        }
    }

    @Override
    protected void onStart() {
        // 如果要显示位置图标,必须先开启图层定位
        mBaiduMap.setMyLocationEnabled(true);
        if (!mLocationClient.isStarted()) {
            mLocationClient.start();
        }
        super.onStart();
    }

    @Override
    protected void onDestroy() {

        // 在activity执行onDestroy时执行mMapView.onDestroy()
        mLocationClient.stop();
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 在activity执行onResume时执行mMapView. onResume ()
        mMapView.onResume();
    }

    @Override
    protected void onPause() {

        // 在activity执行onPause时执行mMapView. onPause ()
        mMapView.onPause();
        super.onPause();
    }


}
