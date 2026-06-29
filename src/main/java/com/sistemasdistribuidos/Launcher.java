package com.sistemasdistribuidos;

import com.sistemasdistribuidos.cliente.ClienteGUI;
import com.sistemasdistribuidos.servidor.ServidorGUI;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

// Ponto de entrada único do JAR empacotado. Permite abrir o Servidor ou o Cliente
// a partir do mesmo executável:
//   java -jar clienteservidor.jar              → mostra um diálogo de escolha
//   java -jar clienteservidor.jar servidor     → abre direto o Servidor
//   java -jar clienteservidor.jar cliente      → abre direto o Cliente
public class Launcher {

    public static void main(String[] args) {
        Tema.aplicar();

        if (args.length > 0) {
            String modo = args[0].toLowerCase();
            if (modo.startsWith("serv")) { ServidorGUI.abrir(); return; }
            if (modo.startsWith("cli"))  { ClienteGUI.abrir();  return; }
        }

        SwingUtilities.invokeLater(Launcher::escolher);
    }

    private static void escolher() {
        Object[] opcoes = {"Servidor", "Cliente", "Sair"};
        int escolha = JOptionPane.showOptionDialog(
                null,
                "O que você deseja iniciar?",
                "Sistema Cliente-Servidor",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, opcoes, opcoes[0]);

        if (escolha == 0)      ServidorGUI.abrir();
        else if (escolha == 1) ClienteGUI.abrir();
        else                   System.exit(0);
    }
}
