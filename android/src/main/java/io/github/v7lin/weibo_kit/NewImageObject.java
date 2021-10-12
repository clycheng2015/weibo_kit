package io.github.v7lin.weibo_kit;

import android.graphics.Bitmap;

import com.sina.weibo.sdk.api.ImageObject;
import com.sina.weibo.sdk.c.c;

import java.io.ByteArrayOutputStream;

public class NewImageObject extends ImageObject {
    @Override
    public void setImageData(Bitmap var1) {
        try {
            ByteArrayOutputStream var2 = new ByteArrayOutputStream();
            var1.compress(Bitmap.CompressFormat.PNG, 85, var2);
            this.imageData = var2.toByteArray();
            var2.close();
        } catch (Exception var3) {
            c.b("ImageObject", "ImageObject :" + var3.getMessage());
        }
    }
}
