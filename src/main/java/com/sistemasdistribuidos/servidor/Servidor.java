package com.sistemasdistribuidos.servidor;

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

    private final int porta;
    private final Map<String, JSONObject> bancoUsuarios = new ConcurrentHashMap<>();
    private final Map<String, String>     sessoes       = new ConcurrentHashMap<>(); // token → usuario

    public Servidor(int porta) {
        this.porta = porta;
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

        String token = "usr_" + usuario;
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

    // ─── AUXILIARES ───────────────────────────────────────────────────────────

    private String autenticar(JSONObject dados, PrintWriter saida) {
        if (!dados.has("token")) {
            enviarErro(saida, "Campo obrigatório ausente: 'token'.");
            return null;
        }

        String token = dados.getString("token").trim();

        if (token.isEmpty()) {
            enviarErro(saida, "O campo 'token' não pode estar vazio.");
            return null;
        }

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
        new Servidor(8080).iniciar();
    }
}