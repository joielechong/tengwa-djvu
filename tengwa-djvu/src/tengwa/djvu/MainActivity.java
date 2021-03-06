package tengwa.djvu;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.Toast;

public class MainActivity extends Activity implements DataCatListener{
    public static final int GO_TO_DIALOG = 1;
    public static final int ABOUT_DIALOG = 2;
    public static final int LOADING_DIALOG = 3;
    public static final int ERROR_DIALOG = 4;

    public static final int SETTINGS_ACTIVITY = 10;
    public static final int OPEN_FILE_ACTIVITY = 11;

    private static final String FILE_PATH = "file path";
    private static final String CURRENT_PAGE = "current page";

    private RecentDbAdapter mDbAdapter;
    private DataCatBase mDataCat;
    static int mCurrentPage;
    static FileInfo mFileInfo;
    private int mError;

    private Toast mPageToast;
    private Dialog mLoadingDialog;
    private PageView mPage;
    private GestureDetector mDetector;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        setTitle(R.string.app_name);

        mPageToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mPageToast.setGravity(Gravity.TOP | Gravity.RIGHT, 0, 0);

        mPage = (PageView) findViewById(R.id.doc_view);

        loadPreferences();

        mDbAdapter = new RecentDbAdapter(getApplicationContext());
        mDbAdapter.open();

        mDataCat = new DataCat();
        mDataCat.bind(this);
        Djvulibre.errorCall = mDataCat;
        Djvulibre.docinfoCall = mDataCat;
        Djvulibre.pageinfoCall = mDataCat;
        Djvulibre.redisplayCall = mDataCat;

        if (savedInstanceState == null){
            startActivityForResult(new Intent(this, OpenFileActivity.class), OPEN_FILE_ACTIVITY);
        } else {
            showDialog(LOADING_DIALOG);
            mCurrentPage = savedInstanceState.getInt(CURRENT_PAGE);
            mDataCat.loadFile(savedInstanceState.getString(FILE_PATH));
        }

        mDetector = new GestureDetector(this, new SimpleOnGestureListener(){
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                                    float distanceY) {
                mPage.scroll(distanceX, distanceY);
                return false;
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        if (mFileInfo != null) {
            outState.putString(FILE_PATH, mFileInfo.filePath);
            outState.putInt(CURRENT_PAGE, mCurrentPage);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFileInfo != null) {
            mDbAdapter.create(mFileInfo.filePath, mCurrentPage);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Djvulibre.documentRelease();
        Djvulibre.contextRelease();
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
        case R.id.menu_item_about:
            showDialog(ABOUT_DIALOG);
            break;
        case R.id.menu_item_open:
            startActivityForResult(new Intent(this, OpenFileActivity.class), OPEN_FILE_ACTIVITY);
            break;
        case R.id.menu_item_goto:
            showDialog(GO_TO_DIALOG);
            break;
        case R.id.menu_item_settings:
            startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_ACTIVITY);
            break;
        }
        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id){
        AlertDialog.Builder builder;
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        switch (id){
        case ABOUT_DIALOG:
            builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.about);
            builder.setView(inflater.inflate(R.layout.about_dialog,
                    (ViewGroup) findViewById(R.layout.main)));
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                  }

            });
            return builder.create();
        case GO_TO_DIALOG:
            GoToDialog dialog = new GoToDialog(this);

            dialog.setGoToPageAction(new GoToDialog.GoToPageAction(){
                public void goToPage(int page) {
                    if (page > 0 && page <= mFileInfo.pageTotal) {
                        mCurrentPage = page;
                        showDialog(LOADING_DIALOG);
                        mDataCat.getPage(page);
                    }
                }
            });
            return dialog;
        case LOADING_DIALOG:
            if (mLoadingDialog == null) {
                mLoadingDialog = ProgressDialog.show(this, null, getString(R.string.loading), true);
            }
            return  mLoadingDialog;
        case ERROR_DIALOG:
            builder = new AlertDialog.Builder(this);
            switch (mError){
            case DataCatListener.ERROR_FILE_NOT_FOUND:
                builder.setMessage(R.string.error_file_not_found);
                break;
            case DataCatListener.ERROR_FILE_NOT_OPENED:
                builder.setMessage(R.string.error_file_not_opened);
                break;
            case DataCatListener.ERROR_NO_SUCH_PAGE:
                builder.setMessage(R.string.error_no_such_page);
                break;
            case DataCatListener.ERROR_WRONG_FILE_FORMAT:
                builder.setMessage(R.string.error_wrong_file_format);
                break;
            }
            builder.setCancelable(false)
                   .setTitle(R.string.error)
                   .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           startActivityForResult(new Intent(MainActivity.this,
                                   OpenFileActivity.class), OPEN_FILE_ACTIVITY);
                       }
                   });
            return builder.create();
        }
        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent){
        switch (requestCode){
        case SETTINGS_ACTIVITY:
            loadPreferences();
            break;
        case OPEN_FILE_ACTIVITY:
            if (resultCode == OpenFileActivity.CLOSE_APPLICATION) {
                moveTaskToBack(true);
            }else if (resultCode == RESULT_OK){
                Bundle data = intent.getExtras();
                String path = data.getString(RecentDbAdapter.KEY_PATH);
                String name = data.getString(RecentDbAdapter.KEY_NAME);
                mCurrentPage = data.getInt(RecentDbAdapter.KEY_PAGE, 1);
                if (name == null){
                    int first = path.lastIndexOf('/'), last = path.lastIndexOf('.');
                    name = path.substring(first + 1, last);
                }
                setTitle(name + " - " + getString(R.string.app_name));
                showDialog(LOADING_DIALOG);
                mDataCat.loadFile(path);
            } else if (mFileInfo == null) {
                Toast.makeText(this, R.string.should_select_file, Toast.LENGTH_LONG).show();
                startActivityForResult(new Intent(this,OpenFileActivity.class), OPEN_FILE_ACTIVITY);
            }
            break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mDetector.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            startActivityForResult(new Intent(this, OpenFileActivity.class), OPEN_FILE_ACTIVITY);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void loadPreferences(){
        if (mDataCat != null)
            mDataCat.updatePreferences();

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        View toolbar = findViewById(R.id.toolbar);
        if (sp.getBoolean("toolbar_on", true)){
            toolbar.setVisibility(View.VISIBLE);
        } else {
            toolbar.setVisibility(View.GONE);
        }
        mPage.loadPreferences(sp);
    }

    public void onToolbarClick(View view){
        switch (view.getId()){
        case R.id.toolbar_zoom_down:
            mPage.zoomOut();
            break;
        case R.id.toolbar_zoom_up:
            mPage.zoomIn();
            break;
        case R.id.toolbar_prev:
            if (mCurrentPage > 1 && mDataCat != null) {
                showDialog(LOADING_DIALOG);
                mDataCat.getPage(mCurrentPage - 1);
                --mCurrentPage;
            }
            break;
        case R.id.toolbar_next:
            if (mCurrentPage < mFileInfo.pageTotal && mDataCat != null) {
                showDialog(LOADING_DIALOG);
                mDataCat.getPage(mCurrentPage + 1);
                ++mCurrentPage;
            }
            break;
        }
    }

    /*
     * Methods for implementing DataCatListener
     */

    public void takePage(Bitmap page) {
        mPage.setPage(page);
        mLoadingDialog.dismiss();
        mPageToast.setText(mCurrentPage + "/" + mFileInfo.pageTotal);
        mPageToast.show();
    }

    public void takeFileInfo(FileInfo fileInfo) {
        mFileInfo = fileInfo;
        mDbAdapter.create(fileInfo.filePath, mCurrentPage);
        mDataCat.getPage(mCurrentPage);
    }

    public void takeError(int errorDescription) {
        mError = errorDescription;
        if (mLoadingDialog != null) {
            mLoadingDialog.setOnCancelListener(new DialogInterface.OnCancelListener(){
                public void onCancel(DialogInterface dialogInterface) {
                    MainActivity.this.showDialog(ERROR_DIALOG);
                }
            });
            mLoadingDialog.cancel();
        }
    }
}
