package com.sistemasdistribuidos.servidor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Servidor {

    // Conta de administrador padrão — sempre disponível, login fixo admin/123456.
    private static final String ADMIN_USUARIO = "admin";
    private static final String ADMIN_SENHA   = "123456";

    private static final String ROLE_USER  = "usr";  // sessão de usuário comum
    private static final String ROLE_ADMIN = "adm";  // sessão de administrador

    private final int porta;
    private final Map<String, JSONObject> bancoUsuarios = new ConcurrentHashMap<>();
    private final Map<String, String>     sessoes       = new ConcurrentHashMap<>(); // token → usuario

    public Servidor(int porta) {
        this.porta = porta;
        semearAdmin();
    }

    // Garante que a conta de administrador exista desde a inicialização.
    private void semearAdmin() {
        JSONObject admin = new JSONObject();
        admin.put("nome",    "Administrador");
        admin.put("usuario", ADMIN_USUARIO);
        admin.put("senha",   ADMIN_SENHA);
        bancoUsuarios.put(ADMIN_USUARIO, admin);
    }

    public void iniciar() {
        System.out.println("[SISTEMA] Servidor aguardando conexões na porta " + porta + "...\n");

        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> lidarComCliente(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("[ERRO CRÍTICO] " + e.getMessage());
        }
    }

    private void lidarComCliente(Socket socket) {
        try (BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {
            System.out.println("[SISTEMA] Cliente conectado: " + socket.getInetAddress() + ":" + socket.getPort());
            String linha;
            while ((linha = entrada.readLine()) != null) {
                try {
                    JSONObject jsonRecebido = new JSONObject(linha);

                    // Log local: imprime o que chegou pela rede — nada é transmitido de volta aqui
                    System.out.println("\n[ RECEBIDO ← CLIENTE ]");
                    System.out.println(jsonRecebido.toString(4));
                    System.out.println("--------------------------------------------------");

                    processarRequisicao(jsonRecebido, saida);

                } catch (JSONException e) {
                    enviarErro(saida, "JSON malformado recebido.");
                }
            }
        } catch (IOException e) {
            // Cliente desconectou
        }
    }

    private void processarRequisicao(JSONObject jsonRecebido, PrintWriter saida) {
        if (!jsonRecebido.has("op")) {
            enviarErro(saida, "Toda mensagem JSON precisa do campo 'op'.");
            return;
        }

        String op = jsonRecebido.getString("op");

        switch (op) {
            case "cadastrarUsuario": cadastrarUsuario(jsonRecebido, saida); break;
            case "login":            login(jsonRecebido, saida);            break;
            case "logout":           logout(jsonRecebido, saida);           break;
            case "consultarUsuario": consultarUsuario(jsonRecebido, saida); break;
            case "atualizarUsuario": atualizarUsuario(jsonRecebido, saida); break;
            case "deletarUsuario":   deletarUsuario(jsonRecebido, saida);   break;

            // ── ENTREGA 2 — ADMIN ──
            case "consultarUsuariosAdmin": consultarUsuariosAdmin(jsonRecebido, saida); break;
            case "consultarUsuarioAdmin":  consultarUsuarioAdmin(jsonRecebido, saida);  break;
            case "atualizarUsuarioAdmin":  atualizarUsuarioAdmin(jsonRecebido, saida);  break;
            case "deletarUsuarioAdmin":    deletarUsuarioAdmin(jsonRecebido, saida);    break;

            default:                 enviarErro(saida, "Operação desconhecida: " + op);
        }
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    private void cadastrarUsuario(JSONObject dados, PrintWriter saida) {
        for (String campo : new String[]{"nome", "usuario", "senha"}) {
            if (!dados.has(campo)) {
                enviarErro(saida, "Campo obrigatório ausente: '" + campo + "'");
                return;
            }
        }

        String nome    = dados.getString("nome").trim();
        String usuario = dados.getString("usuario").trim();
        String senha   = dados.getString("senha").trim();

        if (nome.isEmpty() || usuario.isEmpty() || senha.isEmpty()) {
            enviarErro(saida, "Todos os campos devem estar preenchidos.");
            return;
        }
        if (!usuario.matches("^[a-zA-Z0-9]{5,20}$")) {
            enviarErro(saida, "Usuário inválido. Deve ter entre 5 e 20 caracteres alfanuméricos.");
            return;
        }
        if (!senha.matches("^\\d{6}$")) {
            enviarErro(saida, "Senha inválida. Use apenas números e exatamente 6 dígitos.");
            return;
        }
        if (bancoUsuarios.containsKey(usuario)) {
            enviarErro(saida, "Usuário já cadastrado.");
            return;
        }

        JSONObject registro = new JSONObject();
        registro.put("nome",    nome);
        registro.put("usuario", usuario);
        registro.put("senha",   senha);
        bancoUsuarios.put(usuario, registro);

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("mensagem", "Cadastrado com sucesso");
        enviarJSON(saida, resp);
    }

    // ─── LOGIN ────────────────────────────────────────────────────────────────

    private void login(JSONObject dados, PrintWriter saida) {
        if (!dados.has("usuario") || !dados.has("senha")) {
            enviarErro(saida, "Campos obrigatórios ausentes. Esperado: 'usuario' e 'senha'.");
            return;
        }

        String usuario = dados.getString("usuario").trim();
        String senha   = dados.getString("senha").trim();

        if (usuario.isEmpty() || senha.isEmpty()) {
            enviarErro(saida, "Todos os campos devem estar preenchidos.");
            return;
        }
        if (!usuario.matches("^[a-zA-Z0-9]{5,20}$")) {
            enviarErro(saida, "Usuário inválido. Deve ter entre 5 e 20 caracteres alfanuméricos.");
            return;
        }
        if (!senha.matches("^\\d{6}$")) {
            enviarErro(saida, "Senha inválida. Use apenas números e exatamente 6 dígitos.");
            return;
        }

        JSONObject registro = bancoUsuarios.get(usuario);
        if (registro == null || !registro.getString("senha").equals(senha)) {
            enviarErro(saida, "Usuário ou senha inválidos.");
            return;
        }

        // O admin recebe um token com role "adm"; demais usuários, role "usr".
        String role  = usuario.equals(ADMIN_USUARIO) ? ROLE_ADMIN : ROLE_USER;
        String token = role + "_" + usuario;
        sessoes.put(token, usuario);

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("token",    token);
        enviarJSON(saida, resp);
    }

    // ─── LOGOUT ───────────────────────────────────────────────────────────────

    private void logout(JSONObject dados, PrintWriter saida) {
        if (!dados.has("token")) {
            enviarErro(saida, "Campo obrigatório ausente: 'token'.");
            return;
        }

        String token = dados.getString("token").trim();

        if (token.isEmpty()) {
            enviarErro(saida, "O campo 'token' não pode estar vazio.");
            return;
        }
        if (!sessoes.containsKey(token)) {
            enviarErro(saida, "Erro ao efetuar logout.");
            return;
        }

        sessoes.remove(token);

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("mensagem", "logout efetuado");
        enviarJSON(saida, resp);
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    private void consultarUsuario(JSONObject dados, PrintWriter saida) {
        String usuario = autenticar(dados, saida);
        if (usuario == null) return;

        JSONObject registro = bancoUsuarios.get(usuario);

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("nome",     registro.getString("nome"));
        resp.put("usuario",  registro.getString("usuario"));
        enviarJSON(saida, resp);
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    private void atualizarUsuario(JSONObject dados, PrintWriter saida) {
        String usuario = autenticar(dados, saida);
        if (usuario == null) return;

        if (!dados.has("nome") || !dados.has("senha")) {
            enviarErro(saida, "Todos os campos devem estar preenchidos.");
            return;
        }

        String novoNome  = dados.getString("nome").trim();
        String novaSenha = dados.getString("senha").trim();

        if (novoNome.isEmpty() || novaSenha.isEmpty()) {
            enviarErro(saida, "Todos os campos devem estar preenchidos.");
            return;
        }
        if (!novaSenha.matches("^\\d{6}$")) {
            enviarErro(saida, "Senha inválida. Use apenas números e exatamente 6 dígitos.");
            return;
        }

        JSONObject registro = bancoUsuarios.get(usuario);
        registro.put("nome",  novoNome);
        registro.put("senha", novaSenha);

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("mensagem", "Atualizado com sucesso");
        enviarJSON(saida, resp);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    private void deletarUsuario(JSONObject dados, PrintWriter saida) {
        String usuario = autenticar(dados, saida);
        if (usuario == null) return;

        String token = dados.getString("token").trim();
        sessoes.remove(token);
        bancoUsuarios.remove(usuario);

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("mensagem", "Deletado com sucesso");
        enviarJSON(saida, resp);
    }

    // ═══ ENTREGA 2 — ADMIN ══════════════════════════════════════════════════════

    // ─── ADM LIST (consultar todos usuários) ────────────────────────────────────

    private void consultarUsuariosAdmin(JSONObject dados, PrintWriter saida) {
        if (!autenticarAdmin(dados, saida, "Deve ser ADM para consultar a lista")) return;

        JSONArray lista = new JSONArray();
        for (JSONObject registro : bancoUsuarios.values()) {
            JSONObject item = new JSONObject();
            item.put("usuario", registro.getString("usuario"));
            item.put("nome",    registro.getString("nome"));
            lista.put(item);
        }

        JSONObject resp = new JSONObject();
        resp.put("resposta",       "200");
        resp.put("lista_usuarios", lista);
        enviarJSON(saida, resp);
    }

    // ─── ADM READ (consultar usuário) ───────────────────────────────────────────

    private void consultarUsuarioAdmin(JSONObject dados, PrintWriter saida) {
        if (!autenticarAdmin(dados, saida, "Token Inválido")) return;

        if (!dados.has("usuario") || dados.getString("usuario").trim().isEmpty()) {
            enviarErro(saida, "Campo obrigatório ausente: 'usuario'.");
            return;
        }

        String usuario = dados.getString("usuario").trim();
        JSONObject registro = bancoUsuarios.get(usuario);
        if (registro == null) {
            enviarErro(saida, "Usuário não encontrado.");
            return;
        }

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("nome",     registro.getString("nome"));
        resp.put("usuario",  registro.getString("usuario"));
        enviarJSON(saida, resp);
    }

    // ─── ADM UPDATE (atualizar usuário) ─────────────────────────────────────────

    private void atualizarUsuarioAdmin(JSONObject dados, PrintWriter saida) {
        if (!autenticarAdmin(dados, saida, "Token Inválido")) return;

        if (!dados.has("usuario") || dados.getString("usuario").trim().isEmpty()) {
            enviarErro(saida, "Campo obrigatório ausente: 'usuario'.");
            return;
        }

        String usuario = dados.getString("usuario").trim();
        JSONObject registro = bancoUsuarios.get(usuario);
        if (registro == null) {
            enviarErro(saida, "Usuário não encontrado.");
            return;
        }

        // Atualização parcial: o ADM envia como nulo o que não quer mudar.
        boolean alterouAlgo = false;

        if (dados.has("nome") && !dados.isNull("nome")) {
            String novoNome = dados.getString("nome").trim();
            if (!novoNome.isEmpty()) {
                registro.put("nome", novoNome);
                alterouAlgo = true;
            }
        }

        if (dados.has("senha") && !dados.isNull("senha")) {
            String novaSenha = dados.getString("senha").trim();
            if (!novaSenha.isEmpty()) {
                if (!novaSenha.matches("^\\d{6}$")) {
                    enviarErro(saida, "Senha inválida. Use apenas números e exatamente 6 dígitos.");
                    return;
                }
                registro.put("senha", novaSenha);
                alterouAlgo = true;
            }
        }

        if (!alterouAlgo) {
            enviarErro(saida, "Nenhum campo válido enviado para atualização.");
            return;
        }

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("mensagem", "Usuario atualizado com sucesso");
        enviarJSON(saida, resp);
    }

    // ─── ADM DELETE (deletar usuário) ───────────────────────────────────────────

    private void deletarUsuarioAdmin(JSONObject dados, PrintWriter saida) {
        if (!autenticarAdmin(dados, saida, "Token Inválido")) return;

        if (!dados.has("usuario") || dados.getString("usuario").trim().isEmpty()) {
            enviarErro(saida, "Campo obrigatório ausente: 'usuario'.");
            return;
        }

        String usuario = dados.getString("usuario").trim();
        if (usuario.equals(ADMIN_USUARIO)) {
            enviarErro(saida, "A conta de administrador não pode ser removida.");
            return;
        }
        if (!bancoUsuarios.containsKey(usuario)) {
            enviarErro(saida, "Usuário não encontrado.");
            return;
        }

        bancoUsuarios.remove(usuario);
        sessoes.values().removeIf(u -> u.equals(usuario)); // encerra sessões ativas do usuário removido

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("mensagem", "Usuario deletado com sucesso");
        enviarJSON(saida, resp);
    }

    // ─── AUXILIARES ───────────────────────────────────────────────────────────

    // Autoriza operações de administrador. O admin se autentica via login normal
    // (admin/123456) e usa o token de sessão resultante. Só é admin quem possui
    // uma sessão ativa cujo dono é o usuário ADMIN_USUARIO. msgErro permite a
    // mensagem específica de cada operação conforme o protocolo da disciplina.
    private boolean autenticarAdmin(JSONObject dados, PrintWriter saida, String msgErro) {
        if (!dados.has("token") || dados.isNull("token")) {
            enviarErro(saida, msgErro);
            return false;
        }
        String token   = dados.getString("token").trim();
        String usuario = sessoes.get(token);
        if (usuario == null || !usuario.equals(ADMIN_USUARIO)) {
            enviarErro(saida, msgErro);
            return false;
        }
        return true;
    }

    private String autenticar(JSONObject dados, PrintWriter saida) {
        // Passo 1 — campo presente e não vazio
        if (!dados.has("token")) {
            enviarErro(saida, "Campo obrigatório ausente: 'token'.");
            return null;
        }

        String token = dados.getString("token").trim();

        if (token.isEmpty()) {
            enviarErro(saida, "O campo 'token' não pode estar vazio.");
            return null;
        }

        // Passo 2 e 3 — dividir pelo separador e exigir exatamente 2 partes
        String[] partes = token.split("_");
        if (partes.length != 2) {
            enviarErro(saida, "Token inválido.");
            return null;
        }

        // Passo 4 — extrair role e nome
        String role        = partes[0];
        String nomeUsuario = partes[1];

        // Passo 5 — validar nome: alfanumérico, entre 5 e 20 caracteres
        if (!nomeUsuario.matches("[a-zA-Z0-9]{5,20}")) {
            enviarErro(saida, "Token inválido.");
            return null;
        }

        // Passo 6 — operações de usuário comum exigem role "usr" (admin usa as ops de admin)
        if (!role.equals(ROLE_USER)) {
            enviarErro(saida, "Token inválido.");
            return null;
        }

        // Parse concluído — verifica se há sessão ativa para esse token
        String usuario = sessoes.get(token);
        if (usuario == null) {
            enviarErro(saida, "Token inválido.");
            return null;
        }

        return usuario;
    }

    // Log local + envio pela rede — payload contém apenas o que o protocolo define
    private void enviarJSON(PrintWriter saida, JSONObject json) {
        System.out.println("\n[ ENVIADO → CLIENTE ]");
        System.out.println(json.toString(4));
        System.out.println("==================================================");
        saida.println(json.toString());
    }

    private void enviarErro(PrintWriter saida, String mensagemErro) {
        JSONObject erro = new JSONObject();
        erro.put("resposta", "401");
        erro.put("mensagem", mensagemErro);
        enviarJSON(saida, erro);
    }

    public static void main(String[] args) {
        new Servidor(21111).iniciar();
    }
}