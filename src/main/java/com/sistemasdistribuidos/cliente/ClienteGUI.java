package com.sistemasdistribuidos.cliente;

import com.sistemasdistribuidos.Tema;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Interface gráfica do cliente (itens a, b, c, d, e da rubrica EP-3): conexão por
// IP/porta, autenticação, lista de usuários logados e troca de mensagens (direta
// e broadcast). Toda chamada de rede roda fora da thread de UI.
public class ClienteGUI extends JFrame implements ClienteRede.Ouvinte {

    private static final long TIMEOUT = 5000;
    private static final String CARD_AUTH = "auth";
    private static final String CARD_CHAT = "chat";

    // Conexão
    private final JTextField campoIp    = new JTextField("127.0.0.1", 12);
    private final JTextField campoPorta = new JTextField("21111", 6);
    private final JButton    botaoConectar = new JButton("Conectar");
    private final JLabel     statusConexao = new JLabel("Desconectado");

    // Autenticação
    private final JTextField     campoNome    = new JTextField(16);
    private final JTextField     campoUsuario = new JTextField(16);
    private final JPasswordField campoSenha   = new JPasswordField(16);

    // Chat
    private final DefaultListModel<String> modeloLogados = new DefaultListModel<>();
    private final JList<String> listaLogados = new JList<>(modeloLogados);
    private final JTextArea  areaChat     = new JTextArea();
    private final JTextArea  areaLog      = new JTextArea();
    private final JTextField campoDestino = new JTextField(12);
    private final JCheckBox  caixaTodos   = new JCheckBox("Para todos (/todos)");
    private final JTextField campoMensagem = new JTextField(22);
    private final JLabel     rotuloToken  = new JLabel("—");

    private final CardLayout cards = new CardLayout();
    private final JPanel painelCentral = new JPanel(cards);

    private ClienteRede rede;
    private String token;

    public ClienteGUI() {
        super("Cliente de Chat — Sistemas Distribuídos");
        montar();
    }

    public static void abrir() {
        Tema.aplicar();
        SwingUtilities.invokeLater(() -> new ClienteGUI().setVisible(true));
    }

    private void montar() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(820, 560);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        add(barraConexao(), BorderLayout.NORTH);

        painelCentral.add(cardAuth(), CARD_AUTH);
        painelCentral.add(cardChat(), CARD_CHAT);
        add(painelCentral, BorderLayout.CENTER);

        atualizarEstado(false);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (rede != null) rede.fechar();
            }
        });
    }

    // ─── Barra de conexão (IP / Porta) ──────────────────────────────────────────

    private JPanel barraConexao() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        barra.add(new JLabel("IP:"));
        barra.add(campoIp);
        barra.add(new JLabel("Porta:"));
        barra.add(campoPorta);
        barra.add(botaoConectar);
        barra.add(new JLabel("  "));
        statusConexao.setOpaque(true);
        statusConexao.setForeground(Color.WHITE);
        statusConexao.setBackground(Color.GRAY);
        statusConexao.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        barra.add(statusConexao);

        botaoConectar.addActionListener(e -> {
            if (rede == null || !rede.isConectado()) conectar();
            else                                     desconectar();
        });
        return barra;
    }

    // ─── Card de autenticação (login / cadastro) ────────────────────────────────

    private JPanel cardAuth() {
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.setBorder(BorderFactory.createTitledBorder("Acesso"));
        form.add(new JLabel("Nome (só p/ cadastro):"));
        form.add(campoNome);
        form.add(new JLabel("Usuário:"));
        form.add(campoUsuario);
        form.add(new JLabel("Senha:"));
        form.add(campoSenha);

        JButton botaoLogin    = new JButton("Login");
        JButton botaoCadastro = new JButton("Cadastrar");
        botaoLogin.addActionListener(e -> login());
        botaoCadastro.addActionListener(e -> cadastrar());
        form.add(botaoLogin);
        form.add(botaoCadastro);

        // Centraliza o formulário sem esticá-lo por toda a janela.
        JPanel wrap = new JPanel();
        wrap.add(Box.createHorizontalStrut(20));
        form.setPreferredSize(new Dimension(420, 170));
        wrap.add(form);
        return wrap;
    }

    // ─── Card de chat ───────────────────────────────────────────────────────────

    private JPanel cardChat() {
        JPanel painel = new JPanel(new BorderLayout(8, 8));

        // Oeste: lista de logados + token + ações de sessão.
        JScrollPane spLista = new JScrollPane(listaLogados);
        spLista.setBorder(BorderFactory.createTitledBorder("Usuários logados"));
        spLista.setPreferredSize(new Dimension(200, 0));

        JButton botaoAtualizar = new JButton("Atualizar lista");
        botaoAtualizar.addActionListener(e -> atualizarLista());
        JButton botaoLogout = new JButton("Logout");
        botaoLogout.addActionListener(e -> logout());

        // Clicar duas vezes num usuário preenche o destinatário.
        listaLogados.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && listaLogados.getSelectedValue() != null) {
                    caixaTodos.setSelected(false);
                    campoDestino.setEnabled(true);
                    campoDestino.setText(listaLogados.getSelectedValue());
                }
            }
        });

        JPanel oeste = new JPanel(new BorderLayout(4, 4));
        oeste.add(spLista, BorderLayout.CENTER);
        JPanel oesteSul = new JPanel(new GridLayout(0, 1, 4, 4));
        oesteSul.add(botaoAtualizar);
        JPanel tokenLinha = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tokenLinha.add(new JLabel("Token:"));
        rotuloToken.setFont(rotuloToken.getFont().deriveFont(Font.BOLD));
        tokenLinha.add(rotuloToken);
        oesteSul.add(tokenLinha);
        oesteSul.add(botaoLogout);
        oeste.add(oesteSul, BorderLayout.SOUTH);
        painel.add(oeste, BorderLayout.WEST);

        // Centro: abas Conversa / Log.
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaLog.setEditable(false);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Conversa", new JScrollPane(areaChat));
        abas.addTab("Log (protocolo)", new JScrollPane(areaLog));
        painel.add(abas, BorderLayout.CENTER);

        // Sul: linha de envio.
        JButton botaoEnviar = new JButton("Enviar");
        botaoEnviar.addActionListener(e -> enviarMensagem());
        campoMensagem.addActionListener(e -> enviarMensagem()); // Enter envia

        caixaTodos.addActionListener(e -> {
            campoDestino.setEnabled(!caixaTodos.isSelected());
            if (caixaTodos.isSelected()) campoDestino.setText("/todos");
            else                         campoDestino.setText("");
        });

        JPanel envio = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        envio.add(new JLabel("Para:"));
        envio.add(campoDestino);
        envio.add(caixaTodos);
        envio.add(new JLabel("Msg:"));
        envio.add(campoMensagem);
        envio.add(botaoEnviar);
        painel.add(envio, BorderLayout.SOUTH);

        return painel;
    }

    // ─── Ações ──────────────────────────────────────────────────────────────────

    private void conectar() {
        String ip = campoIp.getText().trim();
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
            if (porta < 1 || porta > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Porta inválida (1–65535).", "Conexão", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            rede = new ClienteRede(ip, porta, this);
            token = null;
            atualizarEstado(true);
            cards.show(painelCentral, CARD_AUTH);
            definirStatus("Conectado a " + ip + ":" + porta, new Color(0x1565C0));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Não foi possível conectar: " + ex.getMessage(),
                    "Conexão", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void desconectar() {
        if (rede != null) rede.fechar();
        // o restante do reset acontece em aoDesconectar()
    }

    private void login() {
        if (semConexao()) return;
        String usuario = campoUsuario.getText().trim();
        String senha   = new String(campoSenha.getPassword());

        JSONObject req = new JSONObject();
        req.put("op", "login");
        req.put("usuario", usuario);
        req.put("senha", senha);

        emTrabalho(() -> rede.enviarEAguardar(req, TIMEOUT), resp -> {
            if (resp != null && "200".equals(resp.optString("resposta"))) {
                token = resp.getString("token");
                rotuloToken.setText(token);
                cards.show(painelCentral, CARD_CHAT);
                appendChat("[Conectado como " + usuario + "]");
                atualizarLista(); // protocolo: lista pedida automaticamente após o login
            } else {
                JOptionPane.showMessageDialog(this, mensagemDe(resp, "Falha no login."),
                        "Login", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void cadastrar() {
        if (semConexao()) return;
        JSONObject req = new JSONObject();
        req.put("op", "cadastrarUsuario");
        req.put("nome", campoNome.getText().trim());
        req.put("usuario", campoUsuario.getText().trim());
        req.put("senha", new String(campoSenha.getPassword()));

        emTrabalho(() -> rede.enviarEAguardar(req, TIMEOUT), resp -> {
            if (resp != null && "200".equals(resp.optString("resposta"))) {
                JOptionPane.showMessageDialog(this, "Cadastrado com sucesso! Agora faça login.",
                        "Cadastro", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, mensagemDe(resp, "Falha no cadastro."),
                        "Cadastro", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void logout() {
        if (semConexao() || token == null) return;
        JSONObject req = new JSONObject();
        req.put("op", "logout");
        req.put("token", token);

        emTrabalho(() -> rede.enviarEAguardar(req, TIMEOUT), resp -> {
            token = null;
            rotuloToken.setText("—");
            modeloLogados.clear();
            cards.show(painelCentral, CARD_AUTH);
        });
    }

    private void atualizarLista() {
        if (semConexao() || token == null) return;
        JSONObject req = new JSONObject();
        req.put("op", "listarUsuariosLogados");
        req.put("token", token);

        emTrabalho(() -> rede.enviarEAguardar(req, TIMEOUT), resp -> {
            if (resp == null) return;
            JSONArray lista = resp.optJSONArray("lista_usuarios"); // sucesso vem sem 'resposta'
            if (lista == null) return;
            modeloLogados.clear();
            for (int i = 0; i < lista.length(); i++) modeloLogados.addElement(lista.getString(i));
        });
    }

    private void enviarMensagem() {
        if (semConexao() || token == null) return;
        String destino  = caixaTodos.isSelected() ? "/todos" : campoDestino.getText().trim();
        String mensagem = campoMensagem.getText();

        if (destino.isEmpty() || mensagem.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Preencha destinatário e mensagem.",
                    "Enviar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JSONObject req = new JSONObject();
        req.put("op", "enviarMensagem");
        req.put("token", token);
        req.put("destinatario", destino);
        req.put("mensagem", mensagem);

        // Envio fire-and-forget; um eventual erro (ex.: offline) chega depois via push.
        rede.enviar(req);
        appendChat("você → " + destino + ": " + mensagem);
        campoMensagem.setText("");
    }

    // ─── ClienteRede.Ouvinte (vem de thread de rede → volta para a EDT) ─────────

    @Override
    public void aoReceberMensagem(String remetente, String mensagem) {
        appendChat(remetente + ": " + mensagem);
    }

    @Override
    public void aoLog(String linha) {
        SwingUtilities.invokeLater(() -> {
            areaLog.append(linha + "\n");
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        });
    }

    @Override
    public void aoDesconectar() {
        SwingUtilities.invokeLater(() -> {
            token = null;
            rotuloToken.setText("—");
            modeloLogados.clear();
            atualizarEstado(false);
            cards.show(painelCentral, CARD_AUTH);
            definirStatus("Desconectado", Color.GRAY);
        });
    }

    // ─── Auxiliares de UI ───────────────────────────────────────────────────────

    private void appendChat(String texto) {
        SwingUtilities.invokeLater(() -> {
            areaChat.append(texto + "\n");
            areaChat.setCaretPosition(areaChat.getDocument().getLength());
        });
    }

    // Executa a chamada de rede numa thread de trabalho e trata o resultado na EDT.
    private void emTrabalho(Supplier<JSONObject> acao, Consumer<JSONObject> aoConcluir) {
        new Thread(() -> {
            JSONObject resp = acao.get();
            SwingUtilities.invokeLater(() -> aoConcluir.accept(resp));
        }, "cliente-trabalho").start();
    }

    private boolean semConexao() {
        if (rede == null || !rede.isConectado()) {
            JOptionPane.showMessageDialog(this, "Conecte-se ao servidor primeiro.",
                    "Sem conexão", JOptionPane.WARNING_MESSAGE);
            return true;
        }
        return false;
    }

    private String mensagemDe(JSONObject resp, String padrao) {
        if (resp == null) return "Sem resposta do servidor.";
        return resp.optString("mensagem", padrao);
    }

    private void atualizarEstado(boolean conectado) {
        campoIp.setEnabled(!conectado);
        campoPorta.setEnabled(!conectado);
        botaoConectar.setText(conectado ? "Desconectar" : "Conectar");
        painelCentral.setVisible(conectado);
        if (!conectado) definirStatus("Desconectado", Color.GRAY);
    }

    private void definirStatus(String texto, Color cor) {
        statusConexao.setText(texto);
        statusConexao.setBackground(cor);
    }

    public static void main(String[] args) {
        abrir();
    }
}
