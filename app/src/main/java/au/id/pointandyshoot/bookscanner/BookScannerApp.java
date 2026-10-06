package au.id.pointandyshoot.bookscanner;

import android.app.Application;
import java.io.File;

/** Discard orphaned temporary captures after a killed process, before new sessions start. */
public final class BookScannerApp extends Application {
    @Override public void onCreate(){
        super.onCreate();File[] directories=getCacheDir().listFiles();if(directories==null)return;
        for(File directory:directories)if(directory.isDirectory()&&directory.getName().startsWith("shelf-photos-")){
            File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();
        }
    }
}
