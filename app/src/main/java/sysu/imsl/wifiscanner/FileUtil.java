package sysu.imsl.wifiscanner;

import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

    public static String file_path = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/WiFiScanData/";

    public static boolean saveSensorData(String file_name, String sensor_data){
        createFilePath();
        try {
            FileWriter writer;
            writer = new FileWriter(createFile(file_name), true);
            writer.append(sensor_data);
            writer.flush();
            writer.close();
            return true;
        } catch (IOException e) {
            Log.e("File write error", e.getMessage() );
        }
        return false;
    }

    public static File createFile(String file_name){
        File file = new File(file_path + file_name);
        try{
            if(!file.exists())
                file.createNewFile();
            Log.d("File create", file_name);

        }catch (IOException e ){
            Log.e("File create error", e.getMessage() );

        }
        return file;
    }

    public static void createFilePath(){
        try{
            File file = new File(file_path);
            if(!file.exists()){
                file.mkdirs();
                Log.d("File path create", file_path);
            }
        }
        catch(Exception e){
            Log.e("File path create error", e.getMessage() );
        }
    }

    public List<String> readTxt(String file_name){
        File file = new File(file_path + file_name);
        List<String> data = new ArrayList<>();
        try{
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String tmpStr = null;
            while((tmpStr = reader.readLine())!=null){
                data.add(tmpStr);
            }
        }
        catch(Exception e){
            Log.d("Read TXT error", e.getMessage());
        }
        return data;
    }

}
