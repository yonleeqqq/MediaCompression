package com.test;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.confiz.filechooser.OnCompleteCallback;
import com.confiz.filechooser.adapter.Cell;
import com.confiz.filechooser.fragment.FileChooserFragment;
import com.confiz.filechooser.utils.Constants;
import com.confiz.filechooser.utils.DisplayType;
import com.confiz.filechooser.utils.FileChooserBuilder;
import com.confiz.filechooser.utils.OrderType;

import java.util.ArrayList;

public class LibraryAttachmentActivity extends AppCompatActivity {

    private int mContainerId;
    private FragmentManager fragmentManager;
    private Context mContext;
    private String[] extensions;
    private DisplayType displayType;
    private boolean enableFolders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chooser);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        enableFolders = true;
        displayType = DisplayType.MIXED_VIEW;
        mContext = this;
        mContainerId = R.id.container;
        extensions = new String[] { Constants.MP4, Constants.GP3,
                Constants.MKV };
        fragmentManager = getSupportFragmentManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int result = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (result != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
            }else{
                showMedia();
            }
        } else {
            showMedia();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showMedia();
                }
                return;
            }
        }
    }

    private void showMedia() {
        Fragment fragment = new FileChooserFragment(
                new FileChooserBuilder(mContext, "com.confiz.app").SupportedExtensions(extensions)
                        .setDisplayType(displayType)
                        .setStubThumb(R.drawable.ic_launcher).setIsHideHeader(true)
                        .setEnableMultiSelect(false).setDownSize(96)
                        .enableFolders(enableFolders)
                        .setFileType(Constants.Type.VIDEO)
                        .setFolderIcon(R.drawable.ic_file_folder_open)
                        .setOrderType(OrderType.LATEST_FIRST), new OnCompleteCallback() {
            @Override
            public void callback(ArrayList<Cell> selectedFiles) {
                if(selectedFiles!=null && selectedFiles.size()==1){
                    Intent i = getIntent();
                    i.putExtra("path",selectedFiles.get(0).getFile().getAbsolutePath());
                    setResult(RESULT_OK, i);
                    finish();

                }
            }
        },mContainerId, new ArrayList<Cell>());
        replaceFragment(fragment);
    }

    public void replaceFragment(final Fragment fragment) {
        new Handler().post(new Runnable() {
            public void run() {
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(mContainerId, fragment);
                fragmentTransaction.commit();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_done, menu);
        return super.onCreateOptionsMenu(menu);
    }
}