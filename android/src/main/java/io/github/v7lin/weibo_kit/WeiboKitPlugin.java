package io.github.v7lin.weibo_kit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.sina.weibo.sdk.api.TextObject;
import com.sina.weibo.sdk.api.WebpageObject;
import com.sina.weibo.sdk.api.WeiboMultiMessage;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WbAuthListener;
import com.sina.weibo.sdk.common.UiError;
import com.sina.weibo.sdk.openapi.IWBAPI;
import com.sina.weibo.sdk.openapi.WBAPIFactory;
import com.sina.weibo.sdk.share.WbShareCallback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/**
 * WeiboKitPlugin
 */
public class WeiboKitPlugin implements FlutterPlugin, ActivityAware, PluginRegistry.ActivityResultListener, MethodCallHandler {

    private static class WeiboErrorCode {
        public static final int SUCCESS = 0;//成功
        public static final int USERCANCEL = -1;//用户取消发送
        public static final int SENT_FAIL = -2;//发送失败
        public static final int AUTH_DENY = -3;//授权失败
        public static final int USERCANCEL_INSTALL = -4;//用户取消安装微博客户端
        public static final int PAY_FAIL = -5;//支付失败
        public static final int SHARE_IN_SDK_FAILED = -8;//分享失败 详情见response UserInfo
        public static final int UNSUPPORT = -99;//不支持的请求
        public static final int UNKNOWN = -100;
    }

    private static final String METHOD_REGISTERAPP = "registerApp";
    private static final String METHOD_ISINSTALLED = "isInstalled";
    private static final String METHOD_AUTH = "auth";
    private static final String METHOD_SHARETEXT = "shareText";
    private static final String METHOD_SHAREIMAGE = "shareImage";
    private static final String METHOD_SHAREWEBPAGE = "shareWebpage";

    private static final String METHOD_ONAUTHRESP = "onAuthResp";
    private static final String METHOD_ONSHAREMSGRESP = "onShareMsgResp";

    private static final String ARGUMENT_KEY_APPKEY = "appKey";
    private static final String ARGUMENT_KEY_SCOPE = "scope";
    private static final String ARGUMENT_KEY_REDIRECTURL = "redirectUrl";
    private static final String ARGUMENT_KEY_TEXT = "text";
    private static final String ARGUMENT_KEY_TITLE = "title";
    private static final String ARGUMENT_KEY_DESCRIPTION = "description";
    private static final String ARGUMENT_KEY_THUMBDATA = "thumbData";
    private static final String ARGUMENT_KEY_IMAGEDATA = "imageData";
    private static final String ARGUMENT_KEY_IMAGEURI = "imageUri";
    private static final String ARGUMENT_KEY_WEBPAGEURL = "webpageUrl";

    private static final String ARGUMENT_KEY_RESULT_ERRORCODE = "errorCode";
    private static final String ARGUMENT_KEY_RESULT_ERRORMESSAGE = "errorMessage";
    private static final String ARGUMENT_KEY_RESULT_USERID = "userId";
    private static final String ARGUMENT_KEY_RESULT_ACCESSTOKEN = "accessToken";
    private static final String ARGUMENT_KEY_RESULT_REFRESHTOKEN = "refreshToken";
    private static final String ARGUMENT_KEY_RESULT_EXPIRESIN = "expiresIn";

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private MethodChannel channel;
    private Context applicationContext;
    private ActivityPluginBinding activityPluginBinding;

    private IWBAPI iwbapi;

    private static final double SIZE_LIMIT = 0.8 * 1024;

    // --- FlutterPlugin

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), "v7lin.github.io/weibo_kit");
        channel.setMethodCallHandler(this);
        applicationContext = binding.getApplicationContext();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
        applicationContext = null;
    }

    // --- ActivityAware

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityPluginBinding = binding;
        activityPluginBinding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        activityPluginBinding.removeActivityResultListener(this);
        activityPluginBinding = null;
    }

    // --- ActivityResultListener

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 32973:
                if (iwbapi != null) {
                    iwbapi.authorizeCallback(requestCode, resultCode, data);
                }
                return true;
            case 10001:
                if (iwbapi != null) {
                    iwbapi.doResultIntent(data, new WbShareCallback() {
                        @Override
                        public void onComplete() {
                            Map<String, Object> map = new HashMap<>();
                            map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SUCCESS);
                            if (channel != null) {
                                channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
                            }
                        }

                        @Override
                        public void onError(UiError uiError) {
                            Map<String, Object> map = new HashMap<>();
                            map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SHARE_IN_SDK_FAILED);
                            if (channel != null) {
                                channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
                            }
                        }

                        @Override
                        public void onCancel() {
                            Map<String, Object> map = new HashMap<>();
                            map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.USERCANCEL);
                            if (channel != null) {
                                channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
                            }
                        }
                    });
                }
                return true;
        }
        return false;
    }

    // --- MethodCallHandler

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (METHOD_REGISTERAPP.equals(call.method)) {
            final String appKey = call.argument(ARGUMENT_KEY_APPKEY);
            final String scope = call.argument(ARGUMENT_KEY_SCOPE);
            final String redirectUrl = call.argument(ARGUMENT_KEY_REDIRECTURL);
            iwbapi = WBAPIFactory.createWBAPI(activityPluginBinding.getActivity());
            iwbapi.registerApp(applicationContext, new AuthInfo(applicationContext, appKey, redirectUrl, scope));
            result.success(null);
        } else if (METHOD_ISINSTALLED.equals(call.method)) {
            result.success(iwbapi.isWBAppInstalled());
        } else if (METHOD_AUTH.equals(call.method)) {
            handleAuthCall(call, result);
        } else if (METHOD_SHARETEXT.equals(call.method)) {
            handleShareTextCall(call, result);
        } else if (METHOD_SHAREIMAGE.equals(call.method) ||
                METHOD_SHAREWEBPAGE.equals(call.method)) {
            handleShareMediaCall(call, result);
        } else {
            result.notImplemented();
        }
    }

    private void handleAuthCall(@NonNull MethodCall call, @NonNull Result result) {
        if (iwbapi != null) {
            iwbapi.authorize(new WbAuthListener() {
                @Override
                public void onComplete(Oauth2AccessToken token) {
                    final Map<String, Object> map = new HashMap<>();
                    if (token.isSessionValid()) {
                        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SUCCESS);
                        map.put(ARGUMENT_KEY_RESULT_USERID, token.getUid());
                        map.put(ARGUMENT_KEY_RESULT_ACCESSTOKEN, token.getAccessToken());
                        map.put(ARGUMENT_KEY_RESULT_REFRESHTOKEN, token.getRefreshToken());
                        final long expiresIn = (long) Math.ceil((token.getExpiresTime() - System.currentTimeMillis()) / 1000.0);
                        map.put(ARGUMENT_KEY_RESULT_EXPIRESIN, expiresIn);// 向上取整
                    } else {
                        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.UNKNOWN);
                    }
                    if (channel != null) {
                        channel.invokeMethod(METHOD_ONAUTHRESP, map);
                    }
                }

                @Override
                public void onError(UiError uiError) {
                    final Map<String, Object> map = new HashMap<>();
                    map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.UNKNOWN);
                    if (channel != null) {
                        channel.invokeMethod(METHOD_ONAUTHRESP, map);
                    }
                }

                @Override
                public void onCancel() {
                    final Map<String, Object> map = new HashMap<>();
                    map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.USERCANCEL);
                    if (channel != null) {
                        channel.invokeMethod(METHOD_ONAUTHRESP, map);
                    }
                }
            });
        }
        result.success(null);
    }

    private void handleShareTextCall(@NonNull MethodCall call, @NonNull Result result) {
        final WeiboMultiMessage message = new WeiboMultiMessage();

        final TextObject object = new TextObject();
        object.text = call.argument(ARGUMENT_KEY_TEXT);// 1024

        message.textObject = object;

        if (iwbapi != null) {
            iwbapi.shareMessage(message, false);
        }
        result.success(null);
    }

    private void handleShareMediaCall(@NonNull MethodCall call, @NonNull Result result) {
        final WeiboMultiMessage message = new WeiboMultiMessage();

        if (METHOD_SHAREIMAGE.equals(call.method)) {
            if (call.hasArgument(ARGUMENT_KEY_TEXT)) {
                final TextObject object = new TextObject();
                object.text = call.argument(ARGUMENT_KEY_TEXT);// 1024

                message.textObject = object;
            }

            final NewImageObject object = new NewImageObject();
            if (call.hasArgument(ARGUMENT_KEY_IMAGEDATA)) {
//                object.imageData = call.argument(ARGUMENT_KEY_IMAGEDATA);// 2 * 1024 * 1024
                this.setImageDataFromData(object, (byte[]) call.argument(ARGUMENT_KEY_IMAGEDATA));
            } else if (call.hasArgument(ARGUMENT_KEY_IMAGEURI)) {
                String imageUri = call.argument(ARGUMENT_KEY_IMAGEURI);
//                object.imagePath = Uri.parse(imageUri).getPath();// 512 - 10 * 1024 * 1024
                this.setImageDataFromPath(object, Uri.parse(imageUri).getPath());
            }
            message.mediaObject = object;
        } else if (METHOD_SHAREWEBPAGE.equals(call.method)) {
            final WebpageObject object = new WebpageObject();
            object.identify = UUID.randomUUID().toString();
            object.title = call.argument(ARGUMENT_KEY_TITLE);// 512
            object.description = call.argument(ARGUMENT_KEY_DESCRIPTION);// 1024
            object.thumbData = call.argument(ARGUMENT_KEY_THUMBDATA);// 32 * 1024
            object.defaultText = call.argument(ARGUMENT_KEY_DESCRIPTION);
            object.actionUrl = call.argument(ARGUMENT_KEY_WEBPAGEURL);// 512

            message.mediaObject = object;
        }

        if (iwbapi != null) {
            iwbapi.shareMessage(message, false);
        }
        result.success(null);
    }

    /**
     * 根据path设置图片
     *
     * @param object
     * @param path
     */
    private void setImageDataFromPath(NewImageObject object, String path) {
        FileInputStream fis = null;
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
//            options.inPreferredConfig = Bitmap.Config.RGB_565;
            fis = new FileInputStream(path);
            Bitmap temBitmap = BitmapFactory.decodeStream(fis, null, options);
            bitmap = getZoomImage(temBitmap, SIZE_LIMIT);
            if (bitmap != null) {
                object.setImageData(bitmap);
            }
//            temBitmap.recycle();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                fis.close();
                if (bitmap != null) {
                    bitmap.recycle();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 根据前端传入的字节数组获取图片
     *
     * @param object
     * @param data
     */
    private void setImageDataFromData(NewImageObject object, byte[] data) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
//            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap temBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            bitmap = getZoomImage(temBitmap, SIZE_LIMIT);
            if (bitmap != null) {
                object.setImageData(bitmap);
            }
//            temBitmap.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    /**
     * 图片大小压缩
     *
     * @param bitmap
     * @param sizeLimit
     * @return
     */
    private Bitmap compressBitmap(Bitmap bitmap, double sizeLimit) {
        Bitmap newBitmap = null;
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            // 循环判断压缩后图片是否超过限制大小
            while (baos.toByteArray().length/1024 > sizeLimit) {
                // 清空baos
                baos.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                quality -= 10;
            }
            Log.d("baos.toByteArray()=","len"+baos.toByteArray().length);
            newBitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(baos.toByteArray()), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                baos.close();
                newBitmap.recycle();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return newBitmap;
    }

    /**
     * 图片的缩放方法
     *
     * @param bitmap  ：源图片资源
     * @param maxSize ：图片允许最大空间  单位:KB
     * @return
     */
    public static Bitmap getZoomImage(Bitmap bitmap, double maxSize) {
        if (null == bitmap) {
            return null;
        }
        if (bitmap.isRecycled()) {
            return null;
        }
        // 单位：从 Byte 换算成 KB
        double currentSize = bitmapToByteArray(bitmap, true).length / 1024;
        // 判断bitmap占用空间是否大于允许最大空间,如果大于则压缩,小于则不压缩
        while (currentSize > maxSize) {
            // 计算bitmap的大小是maxSize的多少倍
            double multiple = currentSize / maxSize;
            // 开始压缩：将宽带和高度压缩掉对应的平方根倍
            // 1.保持新的宽度和高度，与bitmap原来的宽高比率一致
            // 2.压缩后达到了最大大小对应的新bitmap，显示效果最好
            bitmap = getZoomImage(bitmap, bitmap.getWidth() / Math.sqrt(multiple), bitmap.getHeight() / Math.sqrt(multiple));
            currentSize = bitmapToByteArray(bitmap, false).length / 1024;
        }
        return bitmap;
    }

    /**
     * 图片的缩放方法
     *
     * @param orgBitmap ：源图片资源
     * @param newWidth  ：缩放后宽度
     * @param newHeight ：缩放后高度
     * @return
     */
    public static Bitmap getZoomImage(Bitmap orgBitmap, double newWidth, double newHeight) {
        if (null == orgBitmap) {
            return null;
        }
        if (orgBitmap.isRecycled()) {
            return null;
        }
        if (newWidth <= 0 || newHeight <= 0) {
            return null;
        }
        // 获取图片的宽和高
        float width = orgBitmap.getWidth();
        float height = orgBitmap.getHeight();
        // 创建操作图片的matrix对象
        Matrix matrix = new Matrix();
        // 计算宽高缩放率
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // 缩放图片动作
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap bitmap = Bitmap.createBitmap(orgBitmap, 0, 0, (int) width, (int) height, matrix, true);
        return bitmap;
    }

    /**
     * bitmap转换成byte数组
     *
     * @param bitmap
     * @param needRecycle
     * @return
     */
    public static byte[] bitmapToByteArray(Bitmap bitmap, boolean needRecycle) {
        if (null == bitmap) {
            return null;
        }
        if (bitmap.isRecycled()) {
            return null;
        }
        ByteArrayOutputStream output = null;
        byte[] result = new byte[0];
        try {
            output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            if (needRecycle) {
                bitmap.recycle();
            }

            result = output.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            output.close();
        } catch (Exception e) {
            Log.e("WeiboKitPlugin", e.toString());
        }
        return result;
    }
}
