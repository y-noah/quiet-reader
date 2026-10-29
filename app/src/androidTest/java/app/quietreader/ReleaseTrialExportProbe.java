package app.quietreader;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import java.io.File;
import java.util.UUID;

/** Read-only isolated release trial snapshot; no production classes or user accounts. */
final class ReleaseTrialExportProbe {
    private final Instrumentation test;
    ReleaseTrialExportProbe(Instrumentation test){this.test=test;}
    void run(){StringBuilder report=new StringBuilder("ISOLATED SYNTHETIC trial snapshot; not user labels or holdout\n");int checks=0,failures=0;
        try{
            Context context=test.getTargetContext();if(!context.getPackageName().equals("app.quietreader.flowprobe")||!Build.HARDWARE.equals("ranchu")||Build.VERSION.SDK_INT!=36)throw new IllegalStateException("Isolated API36 fixture required");
            File source=context.getDatabasePath("recommendation-trials.db"),output=new File(context.getExternalFilesDir(null),"trial-observations-snapshot-"+UUID.randomUUID()+".db");if(!source.isFile()||output.exists())throw new IllegalStateException("Snapshot source absent or target exists");
            int sessions=0,labels=0;
            try(SQLiteDatabase input=SQLiteDatabase.openDatabase(source.getPath(),null,SQLiteDatabase.OPEN_READONLY);SQLiteDatabase snapshot=SQLiteDatabase.openOrCreateDatabase(output,null)){
                input.beginTransaction();snapshot.beginTransaction();
                try{
                    snapshot.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,at INTEGER NOT NULL,payload TEXT NOT NULL)");snapshot.execSQL("CREATE TABLE labels(session TEXT NOT NULL,event TEXT NOT NULL,choice TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(session,event))");snapshot.setVersion(1);
                    try(Cursor rows=input.rawQuery("SELECT id,at,payload FROM sessions ORDER BY at,rowid",null)){
                        while(rows.moveToNext()){if(++sessions>30||rows.getString(2).length()>2*1024*1024)throw new IllegalStateException("Session budget exceeded");ContentValues v=new ContentValues();v.put("id",rows.getString(0));v.put("at",rows.getLong(1));v.put("payload",rows.getString(2));snapshot.insertOrThrow("sessions",null,v);}
                    }
                    try(Cursor rows=input.rawQuery("SELECT session,event,choice,at FROM labels ORDER BY session,event",null)){
                        while(rows.moveToNext()){if(++labels>1800)throw new IllegalStateException("Label budget exceeded");ContentValues v=new ContentValues();v.put("session",rows.getString(0));v.put("event",rows.getString(1));v.put("choice",rows.getString(2));v.put("at",rows.getLong(3));snapshot.insertOrThrow("labels",null,v);}
                    }
                    snapshot.setTransactionSuccessful();
                }finally{snapshot.endTransaction();input.endTransaction();}
                try(Cursor rows=input.rawQuery("SELECT COUNT(*) FROM labels",null)){rows.moveToFirst();if(rows.getInt(0)!=labels)throw new IllegalStateException("Source changed during snapshot");}
            }
            checks++;report.append("PASS consistent sessions and labels copied with read-only source transaction\n");
            if(sessions==0||labels==0)throw new IllegalStateException("No actual trial observations");checks++;report.append("PASS actual release fixture has ").append(sessions).append(" sessions and ").append(labels).append(" labels\n");
            if(!output.setReadable(true,false))throw new IllegalStateException("Cannot expose isolated diagnostic copy");
            report.append("snapshot=").append(output.getName()).append('\n');
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
