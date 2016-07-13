package com.test;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.compression.library.MyListener;
import com.compression.library.VideoResolutionChanger;

import java.io.File;

public class CompressionAsyncTask extends AsyncTask<Void, Void, String>{

    private ProgressDialog pb;
    private Context mContext;
    private VideoResolutionChanger object;
    private String filepath;
    private String outputFilePath;
    private MyListener listener;

    public CompressionAsyncTask(Context context, VideoResolutionChanger obj, String filepath, MyListener listener){
        this.mContext = context;
        this.object = obj;
        this.filepath= filepath;
        this.listener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        pb = ProgressDialog.show(mContext,"Please Wait", "Compressing");
    }

    @Override
    protected String doInBackground(Void... params) {
        try {
            outputFilePath = object.changeResolution(new File(filepath));
        } catch (Throwable throwable) {
            return null;
        }
        return outputFilePath;
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        if(pb!=null){
            pb.dismiss();
        }

        if(outputFilePath!=null){
            Toast.makeText(mContext, "compressed file"+outputFilePath, Toast.LENGTH_SHORT).show();
            if(listener!=null){
                listener.onFinished(outputFilePath);
            }
        }else{
            Toast.makeText(mContext, "Some error occured", Toast.LENGTH_SHORT).show();
        }
    }
}
