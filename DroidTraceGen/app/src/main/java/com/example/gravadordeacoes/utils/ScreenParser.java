package com.example.gravadordeacoes.utils;

import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ScreenParser {

    private ScreenParser() {}

    /**
     * Retorna APENAS o nome da tela identificada (sem formatação extra).
     */
    public static String identifyScreen(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return "Unknown Screen";

        // 1. Tenta identificar abas (Bottom Navigation)
        String selectedTab = detectSelectedTab(rootNode);
        if (selectedTab != null) return selectedTab;

        // 2. Tenta identificar Dialer
        String dialerMode = identifyDialerMode(rootNode);
        if (dialerMode != null) return dialerMode;

        // 3. Estratégia padrão de Título
        String screenTitle = findScreenTitle(rootNode);

        // CORREÇÃO: Ignora se o título parecer um número de telefone (ex: "85", "(11) 9...")
        // Isso evita que o visor do telefone vire o nome da tela.
        if (!"No Title".equals(screenTitle) && !isPhoneNumber(screenTitle)) {
            return screenTitle;
        }

        String rawClassName = findScreenClassName(rootNode);
        if (rawClassName.contains("Layout") || rawClassName.contains("View")) {
            return "Home or Main Screen";
        }
        return rawClassName;
    }

    /**
     * Gera o sufixo padrão de contexto para todos os logs.
     * Formato: " | ScreenName | PackageName"
     */
    public static String getScreenContext(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return " | No Title | Unknown Package";

        // ANTES: String screenName = identifyScreen(rootNode); (Isso repetia o nome da tela)

        // AGORA: Pegamos o Título da Janela Real (ex: "Phone", "WhatsApp")
        String windowTitle = findScreenTitle(rootNode);

        CharSequence pkg = rootNode.getPackageName();
        String packageName = (pkg != null) ? pkg.toString() : "UnknownPkg";

        return " | " + windowTitle + " | " + packageName;
    }

    /**
     * Extrai apenas o TEXTO do elemento (sem verbos).
     */
    public static String getNodeDescription(AccessibilityNodeInfo node) {
        if (node == null) return "Unknown Element";

        String label;

        if (node.isEditable()) {
            label = getLabelForEditableElement(node);
        }
        else if (node.isCheckable()) {
            String mainLabel = describeNodeSemantically(node);
            String state = node.isChecked() ? "[ON]" : "[OFF]";
            label = mainLabel + " " + state;
        }
        else {
            label = describeNodeSemantically(node);
        }

        if ("Unnamed Element".equals(label) || label.isEmpty()) {
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null) {
                String parentLabel = describeNodeSemantically(parent);
                if (!"Unnamed Element".equals(parentLabel)) {
                    label = parentLabel;
                }
            }
        }

        if (("Unnamed Element".equals(label) || label.isEmpty()) && node.getViewIdResourceName() != null) {
            String[] split = node.getViewIdResourceName().split("/");
            if (split.length > 1) label = split[1];
        }

        if ("Unnamed Element".equals(label) || label.isEmpty()) {
            label = getSimpleName(node.getClassName());
        }

        return label;
    }

    public static String getSmartDescription(AccessibilityNodeInfo targetNode) {
        if (targetNode == null) return "";

        AccessibilityNodeInfo parent = targetNode.getParent();
        if (shouldUseParentContext(parent)) {
            String contextText = getAggregatedText(parent);
            if (!contextText.isEmpty()) {
                return contextText;
            }
        }
        return getNodeDescription(targetNode);
    }

    // --- MÉTODOS PRIVADOS AUXILIARES ---

    private static boolean isPhoneNumber(String text) {
        if (text == null) return false;
        // Remove espaços, traços, parênteses e +
        String clean = text.replaceAll("[\\s\\-\\(\\)\\+]", "");
        // Verifica se o restante são apenas dígitos, * ou # e se não está vazio
        return !clean.isEmpty() && clean.matches("^[0-9*#]+$");
    }

    private static boolean shouldUseParentContext(AccessibilityNodeInfo parent) {
        if (parent == null || parent.isScrollable()) return false;
        String parentClass = getSimpleName(parent.getClassName());
        if (parentClass.contains("RecyclerView") || parentClass.contains("ListView") ||
                parentClass.contains("GridView") || parentClass.contains("ViewPager") ||
                parentClass.contains("DrawerLayout")) {
            return false;
        }
        return parent.getChildCount() <= 6;
    }

    private static String getLabelForEditableElement(AccessibilityNodeInfo node) {
        if (node.getHintText() != null) return node.getHintText().toString();
        if (node.getContentDescription() != null) return node.getContentDescription().toString();
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.getHintText() != null) {
            return parent.getHintText().toString();
        }
        return "Text Field";
    }

    private static String describeNodeSemantically(AccessibilityNodeInfo node) {
        if (node == null) return "Unnamed Element";
        List<String> allLabels = new ArrayList<>();
        if (node.getText() != null && !node.getText().toString().trim().isEmpty()) {
            allLabels.add(node.getText().toString().trim());
        }
        if (node.getContentDescription() != null && !node.getContentDescription().toString().trim().isEmpty()) {
            allLabels.add(node.getContentDescription().toString().trim());
        }
        if (allLabels.isEmpty()) {
            allLabels.addAll(findAllChildTexts(node));
        }
        List<String> uniqueLabels = new ArrayList<>(new LinkedHashSet<>(allLabels));
        return uniqueLabels.isEmpty() ? "Unnamed Element" : uniqueLabels.get(0).replace("\n", " ").trim();
    }

    private static String getAggregatedText(AccessibilityNodeInfo node) {
        Set<String> textParts = new LinkedHashSet<>();
        collectTextRecursive(node, textParts);
        return String.join(" ", textParts);
    }

    private static void collectTextRecursive(AccessibilityNodeInfo node, Set<String> parts) {
        if (node == null) return;
        if (node.getText() != null) parts.add(node.getText().toString().trim());
        if (node.getContentDescription() != null) parts.add(node.getContentDescription().toString().trim());
        for (int i = 0; i < node.getChildCount(); i++) {
            collectTextRecursive(node.getChild(i), parts);
        }
    }

    private static String findScreenTitle(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return "No Title";
        AccessibilityWindowInfo window = rootNode.getWindow();
        return (window != null && window.getTitle() != null) ? window.getTitle().toString() : "No Title";
    }

    private static String findScreenClassName(AccessibilityNodeInfo rootNode) {
        if (rootNode.getChildCount() > 0 && rootNode.getChild(0) != null) {
            return getSimpleName(rootNode.getChild(0).getClassName());
        }
        return getSimpleName(rootNode.getClassName());
    }

    public static String detectSelectedTab(AccessibilityNodeInfo rootNode) {
        return findSelectedNodeRecursive(rootNode);
    }

    private static String findSelectedNodeRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isSelected()) {
            if (node.getText() != null) return node.getText().toString();
            if (node.getContentDescription() != null) return node.getContentDescription().toString();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            String found = findSelectedNodeRecursive(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    public static String identifyDialerMode(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText("0");
        if (nodes != null) {
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isClickable() && n.getClassName() != null && n.getClassName().toString().contains("Button")) {
                    return "Keypad";
                }
            }
        }
        return null;
    }

    private static List<String> findAllChildTexts(AccessibilityNodeInfo parent) {
        List<String> texts = new ArrayList<>();
        if (parent == null) return texts;
        for (int i = 0; i < parent.getChildCount(); i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child != null) {
                if (child.getText() != null) texts.add(child.getText().toString().trim());
                else if (child.getContentDescription() != null) texts.add(child.getContentDescription().toString().trim());
                texts.addAll(findAllChildTexts(child));
            }
        }
        return texts;
    }

    private static String getSimpleName(CharSequence className) {
        if (className == null) return "";
        String name = className.toString();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0) ? name.substring(lastDot + 1) : name;
    }
}