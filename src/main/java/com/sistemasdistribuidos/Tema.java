package com.sistemasdistribuidos;

import javax.swing.UIManager;

// Aplica um visual agradável e portátil (Nimbus já vem no JDK — sem extensões).
public final class Tema {

    private static boolean aplicado = false;

    private Tema() { }

    public static void aplicar() {
        if (aplicado) return;
        aplicado = true;
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // Mantém o Look and Feel padrão — a aplicação funciona do mesmo jeito.
        }
    }
}
