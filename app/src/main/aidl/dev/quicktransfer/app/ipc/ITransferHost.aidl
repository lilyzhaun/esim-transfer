package dev.quicktransfer.app.ipc;
import dev.quicktransfer.app.ipc.IHostCallback;
import android.os.ParcelFileDescriptor;
interface ITransferHost {
    String getSnapshot();
    void observe(IHostCallback callback);
    void refresh();
    String prepare(String request);
    void cancelConfirmation(String taskId);
    void resumePolling(String taskId);
    String openWebSheet(String taskId);
    String webSheetEvent(String taskId, String sheetId, String event);
    void leaveWebSheet(String taskId, String sheetId);
    String getTaskLog(String taskId);
    String endLocalTask(String taskId);
    ParcelFileDescriptor exportTaskLog(String taskId);
}
