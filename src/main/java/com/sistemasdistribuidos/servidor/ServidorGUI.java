package com.sistemasdistribuidos.servidor;

import com.sistemasdistribuidos.Tema;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

// Interface gráfica do servidor (itens g, h da rubrica EP-3): campo de porta,
// lista de logados sempre atualizada e log das mensagens enviadas/recebidas.
public class ServidorGUI extends JFrame implements Servidor.Ouvinte {

    private final JTextField campoPorta  = new JTextField("21111", 6);
    private final JButton    botaoLigar   = new JButton("Iniciar");
    private final JLabel     status       = new JLabel("Parado");

    private final DefaultListModel<String> modeloLogados = new DefaultListModel<>();
    private final JList<String> listaLogados = new JList<>(modeloLogados);
    private final JTextArea     areaLog      = new JTextArea();

    private Servidor servidor;

    public ServidorGUI() {
        super("Servidor de Chat — Sistemas Distribuídos");
        montar();
    }

    public static void abrir() {
        Tema.aplicar();
        SwingUtilities.invokeLater(() -> new ServidorGUI().setVisible(true));
    }

    private void montar() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(780, 540);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // ── Barra superior: porta + iniciar/parar + status ──
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topo.add(new JLabel("Porta:"));
        topo.add(campoPorta);
        topo.add(botaoLigar);
        topo.add(new JLabel("   Status:"));
        status.setOpaque(true);
        status.setForeground(Color.WHITE);
        status.setBackground(Color.GRAY);
        status.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        topo.add(status);

        // Mostra o(s) IP(s) desta máquina — é o que os colegas usam no cliente
        // deles para se conectar a este servidor.
        JLabel rotuloIp = new JLabel("Seu IP: " + descobrirIPs());
        rotuloIp.setFont(rotuloIp.getFont().deriveFont(Font.BOLD));
        topo.add(rotuloIp);
        add(topo, BorderLayout.NORTH);

        // ── Centro: lista de logados | log ──
        JScrollPane spLista = new JScrollPane(listaLogados);
        spLista.setBorder(BorderFactory.createTitledBorder("Usuários logados"));
        spLista.setPreferredSize(new Dimension(210, 0));

        areaLog.setEditable(false);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane spLog = new JScrollPane(areaLog);
        spLog.setBorder(BorderFactory.createTitledBorder("Log — mensagens enviadas / recebidas"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, spLista, spLog);
        split.setDividerLocation(215);
        add(split, BorderLayout.CENTER);

        botaoLigar.addActionListener(e -> alternar());
    }

    private void alternar() {
        if (servidor == null || !servidor.isRodando()) ligar();
        else                                            desligar();
    }

    private void ligar() {
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
            if (porta < 1 || porta > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Porta inválida. Use um número entre 1 e 65535.",
                    "Porta", JOptionPane.WARNING_MESSAGE);
            return;
        }

        servidor = new Servidor(porta, this);
        Thread t = new Thread(servidor::iniciar, "servidor");
        t.setDaemon(true);
        t.start();

        campoPorta.setEnabled(false);
        botaoLigar.setText("Parar");
        definirStatus("Rodando na porta " + porta, new Color(0x2E7D32));
    }

    private void desligar() {
        if (servidor != null) servidor.parar();
        campoPorta.setEnabled(true);
        botaoLigar.setText("Iniciar");
        definirStatus("Parado", Color.GRAY);
        modeloLogados.clear();
    }

    private void definirStatus(String texto, Color cor) {
        status.setText(texto);
        status.setBackground(cor);
    }

    // Descobre os IPv4 reais da máquina (ignora loopback/virtuais). É o endereço
    // que os outros computadores da rede usam para alcançar este servidor.
    private static String descobrirIPs() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> enderecos = ni.getInetAddresses();
                while (enderecos.hasMoreElements()) {
                    InetAddress addr = enderecos.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignorado) {
            // Sem rede ou sem permissão para enumerar — cai no fallback abaixo.
        }
        return ips.isEmpty() ? "127.0.0.1 (rede indisponível)" : String.join("  |  ", ips);
    }

    // ── Servidor.Ouvinte — chamado por threads do servidor; volta para a EDT ──

    @Override
    public void log(String linha) {
        SwingUtilities.invokeLater(() -> {
            areaLog.append(linha + "\n");
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        });
    }

    @Override
    public void sessoesAtualizadas(List<String> usuarios) {
        SwingUtilities.invokeLater(() -> {
            modeloLogados.clear();
            for (String u : usuarios) modeloLogados.addElement(u);
        });
    }

    public static void main(String[] args) {
        abrir();
    }
}
