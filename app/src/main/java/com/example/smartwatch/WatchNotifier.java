package com.example.smartwatch;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Collection;
import java.util.List;

/**
 * Wearable MessageClient를 사용해 연결된 워치로 알람 신호를 전송합니다.
 * 반드시 백그라운드 스레드에서 호출해야 합니다.
 */
public class WatchNotifier {

    private static final String TAG = "WatchNotifier";

    private WatchNotifier() {}

    /** 연결된 모든 워치에 알람 시작 메시지를 전송합니다. */
    public static void sendAlarmStart(Context context) {
        sendMessage(context, MessagePaths.ALARM_START);
    }

    /** 연결된 모든 워치에 알람 해제 메시지를 전송합니다. */
    public static void sendAlarmDismiss(Context context) {
        sendMessage(context, MessagePaths.ALARM_DISMISS);
    }

    private static void sendMessage(Context context, String path) {
        try {
            Task<List<Node>> nodeTask = Wearable.getNodeClient(context).getConnectedNodes();
            Collection<Node> nodes = Tasks.await(nodeTask);
            for (Node node : nodes) {
                Tasks.await(
                    Wearable.getMessageClient(context)
                            .sendMessage(node.getId(), path, new byte[0])
                );
                Log.d(TAG, "Sent '" + path + "' to node " + node.getDisplayName());
            }
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected watch nodes found.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send message to watch: " + path, e);
        }
    }
}
