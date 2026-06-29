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
    private final Map<String, Sessao>     sessoes       = new ConcurrentHashMap<>(); // token → sessão (usuário + IP)

    // Sessão amarra o token ao IP do cliente que efetuou o login — detecta fraude
    // quando outro IP tenta reusar o token (itens i/j da rubrica de avaliação).
    private static final class Sessao {
        final String usuario;
        final String ip;
        final PrintWriter saida; // canal de push (S->C) para entregar mensagens a este usuário
        Sessao(String usuario, String ip, PrintWriter saida) {
            this.usuario = usuario;
            this.ip      = ip;
            this.saida   = saida;
        }
    }

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
        String ipCliente = socket.getInetAddress().getHostAddress();
        PrintWriter saida = null;
        try {
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            saida = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("[SISTEMA] Cliente conectado: " + ipCliente + ":" + socket.getPort());
            String linha;
            while ((linha = entrada.readLine()) != null) {
                try {
                    JSONObject jsonRecebido = new JSONObject(linha);

                    // Log local: imprime o que chegou pela rede
                    System.out.println("\n[ RECEBIDO ← CLIENTE " + ipCliente + " ]");
                    System.out.println(jsonRecebido.toString(4));
                    System.out.println("--------------------------------------------------");

                    processarRequisicao(jsonRecebido, saida, ipCliente);

                } catch (JSONException e) {
                    enviarErro(saida, "JSON malformado recebido.");
                } catch (RuntimeException e) {
                    // Blindagem: nenhuma falha ao processar UMA requisição pode derrubar
                    // a thread do cliente — e muito menos o servidor.
                    System.err.println("[ERRO] Falha ao processar requisição de " + ipCliente + ": " + e.getMessage());
                    enviarErro(saida, "Erro interno ao processar a requisição.");
                }
            }
        } catch (IOException e) {
            // Cliente desconectou
        } finally {
            // Fim da conexão: remove as sessões abertas por este cliente, mantendo a
            // lista de usuários logados sempre atualizada (item h da rubrica EP-3).
            if (saida != null) {
                final PrintWriter ref = saida;
                sessoes.values().removeIf(s -> s.saida == ref);
            }
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[SISTEMA] Cliente desconectado: " + ipCliente);
        }
    }

    private void processarRequisicao(JSONObject jsonRecebido, PrintWriter saida, String ipCliente) {
        if (!jsonRecebido.has("op")) {
            enviarErro(saida, "Toda mensagem JSON precisa do campo 'op'.");
            return;
        }

        String op = jsonRecebido.getString("op");

        switch (op) {
            case "cadastrarUsuario": cadastrarUsuario(jsonRecebido, saida); break;
            case "login":            login(jsonRecebido, saida, ipCliente); break;
            case "logout":           logout(jsonRecebido, saida);           break;
            case "consultarUsuario": consultarUsuario(jsonRecebido, saida, ipCliente); break;
            case "atualizarUsuario": atualizarUsuario(jsonRecebido, saida, ipCliente); break;
            case "deletarUsuario":   deletarUsuario(jsonRecebido, saida, ipCliente);   break;

            // ── ENTREGA 2 — ADMIN ──
            case "consultarUsuariosAdmin": consultarUsuariosAdmin(jsonRecebido, saida, ipCliente); break;
            case "consultarUsuarioAdmin":  consultarUsuarioAdmin(jsonRecebido, saida, ipCliente);  break;
            case "atualizarUsuarioAdmin":  atualizarUsuarioAdmin(jsonRecebido, saida, ipCliente);  break;
            case "deletarUsuarioAdmin":    deletarUsuarioAdmin(jsonRecebido, saida, ipCliente);    break;

            // ── ENTREGA 3 — MENSAGENS ──
            case "enviarMensagem":         enviarMensagem(jsonRecebido, saida, ipCliente);         break;
            case "listarUsuariosLogados":  listarUsuariosLogados(jsonRecebido, saida, ipCliente);  break;

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

    private void login(JSONObject dados, PrintWriter saida, String ipCliente) {
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

        // Protocolo: token do admin é literalmente "adm"; demais usuários, "usr_<usuario>".
        // O servidor guarda o token E o IP — qualquer request com esse token vindo
        // de outro IP é tratada como fraude e rejeitada (itens i/j da rubrica).
        String token = usuario.equals(ADMIN_USUARIO) ? ROLE_ADMIN : (ROLE_USER + "_" + usuario);
        sessoes.put(token, new Sessao(usuario, ipCliente, saida));

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

    private void consultarUsuario(JSONObject dados, PrintWriter saida, String ipCliente) {
        String usuario = autenticar(dados, saida, ipCliente);
        if (usuario == null) return;

        JSONObject registro = bancoUsuarios.get(usuario);

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("nome",     registro.getString("nome"));
        resp.put("usuario",  registro.getString("usuario"));
        enviarJSON(saida, resp);
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    private void atualizarUsuario(JSONObject dados, PrintWriter saida, String ipCliente) {
        String usuario = autenticar(dados, saida, ipCliente);
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

    private void deletarUsuario(JSONObject dados, PrintWriter saida, String ipCliente) {
        String usuario = autenticar(dados, saida, ipCliente);
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

    private void consultarUsuariosAdmin(JSONObject dados, PrintWriter saida, String ipCliente) {
        if (!autenticarAdmin(dados, saida, ipCliente, "Deve ser ADM para consultar a lista")) return;

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

    private void consultarUsuarioAdmin(JSONObject dados, PrintWriter saida, String ipCliente) {
        if (!autenticarAdmin(dados, saida, ipCliente, "Token Inválido")) return;

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

    private void atualizarUsuarioAdmin(JSONObject dados, PrintWriter saida, String ipCliente) {
        if (!autenticarAdmin(dados, saida, ipCliente, "Token Inválido")) return;

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

    private void deletarUsuarioAdmin(JSONObject dados, PrintWriter saida, String ipCliente) {
        if (!autenticarAdmin(dados, saida, ipCliente, "Token Inválido")) return;

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
        sessoes.values().removeIf(s -> s.usuario.equals(usuario)); // encerra sessões ativas do usuário removido

        JSONObject resp = new JSONObject();
        resp.put("resposta", "200");
        resp.put("mensagem", "Usuario deletado com sucesso");
        enviarJSON(saida, resp);
    }

    // ═══ ENTREGA 3 — MENSAGENS ══════════════════════════════════════════════════

    // ─── ENVIAR MENSAGEM (direta ou broadcast) ──────────────────────────────────

    private void enviarMensagem(JSONObject dados, PrintWriter saida, String ipCliente) {
        Sessao remetente = resolverSessao(dados, "token", saida, ipCliente);
        if (remetente == null) return;

        if (!dados.has("destinatario") || !dados.has("mensagem")) {
            enviarErro(saida, "Campos obrigatórios ausentes: 'destinatario' e 'mensagem'.");
            return;
        }

        String destinatario = dados.getString("destinatario").trim();
        String mensagem      = dados.getString("mensagem");

        if (destinatario.isEmpty() || mensagem.isEmpty()) {
            enviarErro(saida, "Destinatário e mensagem devem estar preenchidos.");
            return;
        }

        // Broadcast: o protocolo fixa o destinatário "/todos". Entrega a todos os
        // usuários logados, menos o próprio remetente.
        if (destinatario.equals("/todos")) {
            for (Sessao s : sessoes.values()) {
                if (!s.usuario.equals(remetente.usuario)) {
                    empurrarMensagem(s, remetente.usuario, mensagem);
                }
            }
            return;
        }

        // Mensagem direta: regra do protocolo — o destinatário precisa estar online.
        Sessao alvo = buscarSessaoPorUsuario(destinatario);
        if (alvo == null) {
            enviarErro(saida, "Destinatário não está online.");
            return;
        }
        empurrarMensagem(alvo, remetente.usuario, mensagem);
    }

    // ─── LISTAR USUÁRIOS LOGADOS ────────────────────────────────────────────────

    private void listarUsuariosLogados(JSONObject dados, PrintWriter saida, String ipCliente) {
        if (resolverSessao(dados, "token", saida, ipCliente) == null) return;

        JSONArray lista = new JSONArray();
        for (Sessao s : sessoes.values()) {
            lista.put(s.usuario);
        }

        // Protocolo (EP-3): no sucesso a resposta traz APENAS 'lista_usuarios',
        // sem o campo 'resposta'. Respeitado à risca.
        JSONObject resp = new JSONObject();
        resp.put("lista_usuarios", lista);
        enviarJSON(saida, resp);
    }

    // ─── PUSH (S->C): entrega de mensagem ao destinatário ───────────────────────

    private void empurrarMensagem(Sessao alvo, String remetente, String mensagem) {
        JSONObject push = new JSONObject();
        push.put("op",        "receberMensagem");
        push.put("remetente", remetente);
        push.put("mensagem",  mensagem);

        System.out.println("\n[ ENVIADO → CLIENTE " + alvo.usuario + " ] (push)");
        System.out.println(push.toString(4));
        System.out.println("==================================================");
        // println do PrintWriter é atômico por linha; seguro mesmo se a thread do
        // próprio destinatário estiver escrevendo nele ao mesmo tempo.
        alvo.saida.println(push.toString());
    }

    private Sessao buscarSessaoPorUsuario(String usuario) {
        for (Sessao s : sessoes.values()) {
            if (s.usuario.equals(usuario)) return s;
        }
        return null;
    }

    // ─── AUXILIARES ───────────────────────────────────────────────────────────

    // Resolve o token informado (nome do campo varia conforme a operação) para uma
    // sessão ativa, validando presença, existência e o vínculo token↔IP (defesa
    // anti-fraude). Aceita qualquer sessão ativa — comum ou admin — pois a troca de
    // mensagens vale para todos os usuários logados.
    private Sessao resolverSessao(JSONObject dados, String campo, PrintWriter saida, String ipCliente) {
        if (!dados.has(campo) || dados.isNull(campo)) {
            enviarErro(saida, "Campo obrigatório ausente: '" + campo + "'.");
            return null;
        }
        String token = dados.getString(campo).trim();
        if (token.isEmpty()) {
            enviarErro(saida, "O campo '" + campo + "' não pode estar vazio.");
            return null;
        }
        Sessao sessao = sessoes.get(token);
        if (sessao == null) {
            enviarErro(saida, "Token inválido.");
            return null;
        }
        if (!sessao.ip.equals(ipCliente)) {
            System.out.println("[ALERTA] Fraude: token '" + token + "' usado por IP "
                    + ipCliente + " (sessão registrada em " + sessao.ip + ")");
            enviarErro(saida, "Token inválido.");
            return null;
        }
        return sessao;
    }

    // Autoriza operações de administrador. O admin se autentica via login normal
    // (admin/123456) e usa o token de sessão resultante. Só é admin quem possui
    // uma sessão ativa cujo dono é o usuário ADMIN_USUARIO. msgErro permite a
    // mensagem específica de cada operação conforme o protocolo da disciplina.
    private boolean autenticarAdmin(JSONObject dados, PrintWriter saida, String ipCliente, String msgErro) {
        if (!dados.has("token_admin") || dados.isNull("token_admin")) {
            enviarErro(saida, msgErro);
            return false;
        }
        String token  = dados.getString("token_admin").trim();
        Sessao sessao = sessoes.get(token);
        if (sessao == null || !sessao.usuario.equals(ADMIN_USUARIO)) {
            enviarErro(saida, msgErro);
            return false;
        }
        // Token amarrado ao IP de login — outro IP usando o mesmo token = fraude.
        if (!sessao.ip.equals(ipCliente)) {
            System.out.println("[ALERTA] Fraude: token_admin '" + token + "' usado por IP "
                    + ipCliente + " (sessão registrada em " + sessao.ip + ")");
            enviarErro(saida, msgErro);
            return false;
        }
        return true;
    }

    private String autenticar(JSONObject dados, PrintWriter saida, String ipCliente) {
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
        Sessao sessao = sessoes.get(token);
        if (sessao == null) {
            enviarErro(saida, "Token inválido.");
            return null;
        }

        // Defesa contra fraude: o token só vale a partir do IP que efetuou o login.
        // Itens i/j da rubrica: "Servidor não permite que usuário comum consiga
        // editar/apagar dados que não seus".
        if (!sessao.ip.equals(ipCliente)) {
            System.out.println("[ALERTA] Fraude: token '" + token + "' usado por IP "
                    + ipCliente + " (sessão registrada em " + sessao.ip + ")");
            enviarErro(saida, "Token inválido.");
            return null;
        }

        return sessao.usuario;
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