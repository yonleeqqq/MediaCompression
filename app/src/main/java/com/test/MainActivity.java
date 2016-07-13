package com.test;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.compression.library.MyListener;
import com.compression.library.VideoResolutionChanger;
import java.io.File;

public class MainActivity extends AppCompatActivity implements MyListener {

    private Button mBtnVideo;
    private Button mBtnPlay;
    private VideoResolutionChanger obj;
    private String filepath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        File file = new File("/storage/emulated/0/Download/swj1180.pdf");
//        intent.setDataAndType( Uri.fromFile(file), "application/pdf" );
//        startActivity(intent);

        mBtnPlay = (Button) findViewById(R.id.play);
        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(filepath!=null){
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    File file = new File(filepath);
                    viewIntent.setDataAndType(Uri.fromFile(file), "video/*");
                    startActivity(Intent.createChooser(viewIntent, null));
                }else{
                    Toast.makeText(MainActivity.this, "No file input", Toast.LENGTH_LONG).show();
                }
            }
        });


        mBtnVideo = (Button) findViewById(R.id.select_video);
        registerForContextMenu(mBtnVideo);
        mBtnVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent in = new Intent(MainActivity.this, LibraryAttachmentActivity.class);
                startActivityForResult(in, 1);
            }
        });

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_resolution, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id){

            case R.id.action_0:
                obj = new VideoResolutionChanger("com.test", "abc_out.mp4" ,176, 56);
                break;

            case R.id.action_1:
                obj = new VideoResolutionChanger("com.test", "abc_out.mp4" ,640, 1000);
                break;

            case R.id.action_2:
                obj = new VideoResolutionChanger("com.test",  "abc_out.mp4" ,960, 2000);
                break;

            case R.id.action_3:
                obj = new VideoResolutionChanger("com.test",  "abc_out.mp4" ,800, 2000);
                break;

            case R.id.action_4:
                obj = new VideoResolutionChanger("com.test",  "abc_out.mp4" ,1280, 1000);
                break;

            case R.id.action_5:
                obj = new VideoResolutionChanger("com.test",  "abc_out.mp4" ,1920, 5000);
                break;
        }
        if(obj!=null && filepath!=null){
            new CompressionAsyncTask(this,obj,filepath, this).execute();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            if(requestCode==1){
                filepath = data.getExtras().getString("path");
                Toast.makeText(this,"selected file: "+filepath, Toast.LENGTH_SHORT).show();
                openContextMenu(mBtnVideo);
            }
        }
    }

    @Override
    public void onFinished(String filePath) {
        this.filepath = filePath;
    }

    @Override
    public void onCancelled() {
    }
}