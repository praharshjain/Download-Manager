import java.io.*;
import java.net.*;
import java.util.*;

// This class downloads a file from a URL.
class Download extends Observable implements Runnable {
     
    // Max size of download buffer.
    private static final int MAX_BUFFER_SIZE = 1024;
     
    // These are the status names.
    public static final String STATUSES[] = {"Downloading",
    "Paused", "Complete", "Cancelled", "Error"};
     
    // These are the status codes.
    public static final int DOWNLOADING = 0;
    public static final int PAUSED = 1;
    public static final int COMPLETE = 2;
    public static final int CANCELLED = 3;
    public static final int ERROR = 4;
     
    private URL url; // download URL
    private long size; // size of download in bytes
    private long downloaded; // number of bytes downloaded
    private int status; // current status of download
    private long initTime; //inital time when download started or resumed
    private long startTime; // start time for current bytes
    private long readSinceStart; // number of bytes downloaded since startTime
    private long elapsedTime=0; // elapsed time till now
    private long prevElapsedTime=0; // time elapsed before resuming download
    private long remainingTime=-1; //time remaining to finish download
    private float avgSpeed=0; //average download speed in KB/s
    private float speed=0; //download speed in KB/s
    // Constructor for Download.
    public Download(URL url) {
        this.url = url;
        size = -1;
        downloaded = 0;
        status = DOWNLOADING;
        // Begin the download.
        download();
    }
     
    // Get this download's URL.
    public String getUrl() {
        return url.toString();
    }
     
    // Get this download's size.
    public long getSize() {
        return size;
    }
    // Get download speed.
    public float getSpeed() {
        return speed;
    }
    // Get average speed
    public float getAvgSpeed() {
        return avgSpeed;
    }
    // Get elapsed time
    public String getElapsedTime() {
        return formatTime(elapsedTime/1000000000);
    }
    // Get remaining time
    public String getRemainingTime() {
        if(remainingTime<0)   return "Unknown";
        else    return formatTime(remainingTime);
    }
    // Format time
    public String formatTime(long time) { //time in seconds
        String s="";
        s+=(String.format("%02d", time/3600))+":";
        time%=3600;
        s+=(String.format("%02d", time/60))+":";
        time%=60;
        s+=String.format("%02d", time);
        return s;
    }
    // Get this download's progress.
    public float getProgress() {
        return ((float) downloaded / size) * 100;
    }
     
    // Get this download's status.
    public int getStatus() {
        return status;
    }
     
    // Pause this download.
    public void pause() {
        prevElapsedTime=elapsedTime;
        status = PAUSED;
        stateChanged();
    }
     
    // Resume this download.
    public void resume() {
        status = DOWNLOADING;
        stateChanged();
        download();
    }
     
    // Cancel this download.
    public void cancel() {
        prevElapsedTime=elapsedTime;
        status = CANCELLED;
        stateChanged();
    }
     
    // Mark this download as having an error.
    private void error() {
        prevElapsedTime=elapsedTime;
        status = ERROR;
        stateChanged();
    }
     
    // Start or resume downloading.
    private void download() {
        Thread thread = new Thread(this);
        thread.start();
    }
     
    // Get file name portion of URL.
    private String getFileName(URL url) {
        String fileName = url.getFile();
        return fileName.substring(fileName.lastIndexOf('/') + 1);
    }
     
    // Download file.
    public void run() {
        RandomAccessFile file = null;
        InputStream stream = null;
         
        try {
            // Open connection to URL.
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
             
            // Specify what portion of file to download.
            connection.setRequestProperty("Range",
                    "bytes=" + downloaded + "-");
             
            // Connect to server.
            connection.connect();
             
            // Make sure response code is in the 200 range.
            if (connection.getResponseCode() / 100 != 2) {
                error();
            }
             
            // Check for valid content length.
            int contentLength = connection.getContentLength();
            if (contentLength < 1) {
                error();
            }
             
      /* Set the size for this download if it
         hasn't been already set. */
            if (size == -1) {
                size = contentLength;
                stateChanged();
            }
            // used to update speed at regular intervals
            int i=0;
            // Open file and seek to the end of it.
            file = new RandomAccessFile(getFileName(url), "rw");
            file.seek(downloaded);
             
            stream = connection.getInputStream();
            initTime = System.nanoTime();
            while (status == DOWNLOADING) {
        /* Size buffer according to how much of the
           file is left to download. */
                if(i==0)
                {   startTime = System.nanoTime();
                    readSinceStart = 0;
                }
                byte buffer[];
                if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    buffer = new byte[(int)(size - downloaded)];
                }
                // Read from server into buffer.
                int read = stream.read(buffer);
                if (read == -1)
                    break;
                // Write buffer to file.
                file.write(buffer, 0, read);
                downloaded += read;
                readSinceStart+=read;
                //update speed
                i++;
                if(i>=50)
                {   speed=(readSinceStart*976562.5f)/(System.nanoTime()-startTime);
                    if(speed>0) remainingTime=(long)((size-downloaded)/(speed*1024));
                    else remainingTime=-1;
                    elapsedTime=prevElapsedTime+(System.nanoTime()-initTime);
                    avgSpeed=(downloaded*976562.5f)/elapsedTime;
                    i=0;
                }
                stateChanged();
            }
             
      /* Change status to complete if this point was
         reached because downloading has finished. */
            if (status == DOWNLOADING) {
                status = COMPLETE;
                stateChanged();
            }
        } catch (Exception e) {
            System.out.println(e);
            error();
        } finally {
            // Close file.
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {}
            }
             
            // Close connection to server.
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {}
            }
        }
    }
     
    // Notify observers that this download's status has changed.
    private void stateChanged() {
        setChanged();
        notifyObservers();
    }
}