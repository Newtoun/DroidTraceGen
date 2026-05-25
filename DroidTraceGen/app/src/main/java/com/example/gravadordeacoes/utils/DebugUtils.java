package com.example.gravadordeacoes.utils;

import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

public class DebugUtils {

    private static final String TAG = "DebugUtils";

    /**
     * Método estático. Você passa o nó raiz para ele.
     */
    public static void dumpViewHierarchy(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            Log.e(TAG, "HIERARCHY: Root node é null (tela não acessível).");
            return;
        }

        Log.w(TAG, "================ START HIERARCHY DUMP ================");

        try {
            CharSequence packageName = rootNode.getPackageName();
            Log.d(TAG, "APP PACKAGE: " + (packageName != null ? packageName : "Unknown"));

            AccessibilityWindowInfo window = rootNode.getWindow();
            if (window != null) {
                CharSequence title = window.getTitle();
                Log.d(TAG, "WINDOW TITLE: " + (title != null ? title : "Sem título definido"));

            } else {
                Log.d(TAG, "WINDOW INFO: Não foi possível obter (objeto window é null)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao tentar ler dados da janela: " + e.getMessage());
        }
        // -------------------------------------------------------

        printNodeRecursively(rootNode, 0);
        Log.w(TAG, "================ END HIERARCHY DUMP ================");
    }

    private static void printNodeRecursively(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;

        // 1. Criar indentação visual
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("|  ");
        }

        // 2. Coletar dados
        String className = node.getClassName() != null ? node.getClassName().toString() : "null";
        String simpleClass = className.substring(className.lastIndexOf('.') + 1);

        String resourceId = node.getViewIdResourceName();
        if (resourceId != null) {
            resourceId = resourceId.substring(resourceId.lastIndexOf('/') + 1);
        } else {
            resourceId = "no-id";
        }

        String text = (node.getText() != null) ? " Text:['" + node.getText() + "']" : "";
        String desc = (node.getContentDescription() != null) ? " Desc:['" + node.getContentDescription() + "']" : "";
        String clickable = node.isClickable() ? " [CLICKABLE]" : "";
        String scrollable = node.isScrollable() ? " [SCROLLABLE]" : "";

        Log.d(TAG, indent.toString() + simpleClass + " (" + resourceId + ")" + text + desc + clickable + scrollable);

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                printNodeRecursively(child, depth + 1);
                child.recycle();
            }
        }
    }
}