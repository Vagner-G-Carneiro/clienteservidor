package com.sistemasdistribuidos.cliente;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {

    private final String ip;
    private final int    porta;
    private String tokenAtual = null; // null = deslogado

    public Cliente(String ip, int porta) {
        this.ip    = ip;
        this.porta = porta;
    }

    public void iniciar(Scanner scanner) {
        System.out.println("[SISTEMA] Conectando ao servidor " + ip + ":" + porta + "...");

        try (Socket socket = new Socket(ip, porta);
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("[SISTEMA] Conectado com sucesso!\n");

            while (true) {
                exibirMenu();
                String opcao = scanner.nextLine().trim();

                switch (opcao) {
                    case "1": realizarCadastro(scanner, saida, entrada);    break;
                    case "2": realizarLogin(scanner, saida, entrada);       break;
                    case "3": realizarLogout(saida, entrada);               break;
                    case "4": consultarUsuario(saida, entrada);             break;
                    case "5": atualizarUsuario(scanner, saida, entrada);    break;
                    case "6": deletarUsuario(scanner, saida, entrada);      break;
                    case "9": areaAdministrativa(scanner, saida, entrada);  break;
                    default:  System.err.println("Opção inválida! Digite o número correspondente ao menu.");
                }
            }

        } catch (IOException e) {
            System.err.println("[ERRO CRÍTICO] Conexão perdida ou servidor inacessível: " + e.getMessage());
        }
    }

    private void exibirMenu() {
        System.out.println("\n---====== Menu ======---");
        if (tokenAtual == null) {
            System.out.println("| 1 - Cadastrar Usuário |");
            System.out.println("| 2 - Login             |");
        } else {
            System.out.println("| 3 - Logout            |");
            System.out.println("| 4 - Consultar Usuário |");
            System.out.println("| 5 - Atualizar Usuário |");
            System.out.println("| 6 - Deletar Conta     |");
            System.out.println("| Sessão: " + tokenAtual + " |");
        }
        System.out.println("| 9 - Área Administrativa |");
        System.out.println("------------------------");
        System.out.print("Escolha uma opção: ");
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    private void realizarCadastro(Scanner scanner, PrintWriter saida, BufferedReader entrada) throws IOException {
        if (tokenAtual != null) {
            System.err.println("[AVISO] Você já está logado. Faça logout antes de cadastrar outra conta.");
            return;
        }

        System.out.println("\n--- NOVO CADASTRO ---");
        System.out.print("Nome: ");
        String nome = scanner.nextLine();

        System.out.print("Usuário: ");
        String usuario = scanner.nextLine();

        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        JSONObject req = new JSONObject();
        req.put("op",      "cadastrarUsuario");
        req.put("nome",    nome);
        req.put("usuario", usuario);
        req.put("senha",   senha);

        enviarRequisicao(saida, req);
        receberResposta(entrada);
    }

    // ─── LOGIN ────────────────────────────────────────────────────────────────

    private void realizarLogin(Scanner scanner, PrintWriter saida, BufferedReader entrada) throws IOException {
        if (tokenAtual != null) {
            System.err.println("[AVISO] Você já está logado. Faça logout primeiro.");
            return;
        }

        System.out.println("\n--- LOGIN ---");
        System.out.print("Usuário: ");
        String usuario = scanner.nextLine();

        System.out.print("Senha: ");
        String senha = scanner.nextLine();

        JSONObject req = new JSONObject();
        req.put("op",      "login");
        req.put("usuario", usuario);
        req.put("senha",   senha);

        enviarRequisicao(saida, req);
        JSONObject resp = receberResposta(entrada);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            tokenAtual = resp.getString("token");
            System.out.println("[SISTEMA] Token armazenado: " + tokenAtual);
        }
    }

    // ─── LOGOUT ───────────────────────────────────────────────────────────────

    private void realizarLogout(PrintWriter saida, BufferedReader entrada) throws IOException {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Você não está logado.");
            return;
        }

        System.out.println("\n--- LOGOUT ---");

        JSONObject req = new JSONObject();
        req.put("op",    "logout");
        req.put("token", tokenAtual);

        enviarRequisicao(saida, req);
        JSONObject resp = receberResposta(entrada);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            tokenAtual = null;
        }
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    private void consultarUsuario(PrintWriter saida, BufferedReader entrada) throws IOException {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Faça login para consultar seus dados.");
            return;
        }

        System.out.println("\n--- CONSULTAR USUÁRIO ---");

        JSONObject req = new JSONObject();
        req.put("op",    "consultarUsuario");
        req.put("token", tokenAtual);

        enviarRequisicao(saida, req);
        JSONObject resp = receberResposta(entrada);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            System.out.println("[DADOS] Nome:    " + resp.optString("nome"));
            System.out.println("[DADOS] Usuário: " + resp.optString("usuario"));
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    private void atualizarUsuario(Scanner scanner, PrintWriter saida, BufferedReader entrada) throws IOException {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Faça login para atualizar seus dados.");
            return;
        }

        System.out.println("\n--- ATUALIZAR USUÁRIO ---");
        // Rubrica d): "O cliente deve mostrar um campo para a digitação do token
        // antes do envio da mensagem". Mostramos o token atual apenas como dica e
        // deixamos o avaliador digitar/colar livremente — inclusive um token de
        // outro usuário, para verificar que o servidor recusa (item i).
        System.out.println("[DICA] Seu token atual: " + tokenAtual);
        System.out.print("Token: ");
        String token = scanner.nextLine().trim();
        if (token.isEmpty()) {
            System.err.println("[FALHA] Token não pode estar vazio.");
            return;
        }

        System.out.print("Novo nome: ");
        String nome = scanner.nextLine().trim();

        System.out.print("Nova senha: ");
        String senha = scanner.nextLine().trim();

        if (nome.isEmpty() || senha.isEmpty()) {
            System.err.println("[FALHA] Todos os campos devem estar preenchidos.");
            return;
        }

        JSONObject req = new JSONObject();
        req.put("op",    "atualizarUsuario");
        req.put("token", token);
        req.put("nome",  nome);
        req.put("senha", senha);

        enviarRequisicao(saida, req);
        receberResposta(entrada);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    private void deletarUsuario(Scanner scanner, PrintWriter saida, BufferedReader entrada) throws IOException {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Faça login para deletar sua conta.");
            return;
        }

        System.out.println("\n--- DELETAR CONTA ---");
        // Rubrica e): "O cliente deve mostrar um campo para a digitação do token
        // antes do envio da mensagem". O token vai como campo livre para permitir
        // o teste de fraude (item j: servidor recusa token de outro usuário).
        System.out.println("[DICA] Seu token atual: " + tokenAtual);
        System.out.print("Token: ");
        String token = scanner.nextLine().trim();
        if (token.isEmpty()) {
            System.err.println("[FALHA] Token não pode estar vazio.");
            return;
        }

        System.out.print("Tem certeza? Esta ação é irreversível. (s/N): ");
        String confirmacao = scanner.nextLine().trim();

        if (!confirmacao.equalsIgnoreCase("s")) {
            System.out.println("[SISTEMA] Operação cancelada.");
            return;
        }

        JSONObject req = new JSONObject();
        req.put("op",    "deletarUsuario");
        req.put("token", token);

        enviarRequisicao(saida, req);
        JSONObject resp = receberResposta(entrada);

        // Só limpa a sessão local se a conta deletada era de fato a do próprio
        // usuário logado — assim, uma tentativa de fraude com token alheio não
        // afeta o estado do cliente.
        if (resp != null && "200".equals(resp.optString("resposta")) && token.equals(tokenAtual)) {
            tokenAtual = null;
        }
    }

    // ═══ ENTREGA 2 — ÁREA ADMINISTRATIVA ════════════════════════════════════════
    // O administrador autentica fazendo login normal (admin/123456). O token de
    // sessão resultante — com role "adm" — é o que autoriza as operações de admin.

    private void areaAdministrativa(Scanner scanner, PrintWriter saida, BufferedReader entrada) throws IOException {
        System.out.println("\n--- ÁREA ADMINISTRATIVA ---");
        System.out.print("Usuário: ");
        String usuario = scanner.nextLine().trim();
        System.out.print("Senha: ");
        String senha = scanner.nextLine().trim();

        // Autentica via login: somente o admin recebe um token com privilégios.
        JSONObject login = new JSONObject();
        login.put("op",      "login");
        login.put("usuario", usuario);
        login.put("senha",   senha);
        enviarRequisicao(saida, login);
        JSONObject resp = receberResposta(entrada);

        if (resp == null || !"200".equals(resp.optString("resposta"))) {
            return; // mensagem de falha já exibida por receberResposta
        }
        String tokenAdmin = resp.getString("token");
        if (!"adm".equals(tokenAdmin)) {
            System.err.println("[FALHA] Este usuário não possui privilégios de administrador.");
            return;
        }

        try {
            menuAdmin(scanner, tokenAdmin, saida, entrada);
        } finally {
            // Encerra a sessão administrativa ao sair da área.
            JSONObject logout = new JSONObject();
            logout.put("op",    "logout");
            logout.put("token", tokenAdmin);
            enviarRequisicao(saida, logout);
            receberResposta(entrada);
        }
    }

    private void menuAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida, BufferedReader entrada) throws IOException {
        while (true) {
            System.out.println("\n---=== Menu Admin ===---");
            System.out.println("| 1 - Listar todos usuários |");
            System.out.println("| 2 - Consultar usuário     |");
            System.out.println("| 3 - Atualizar usuário     |");
            System.out.println("| 4 - Deletar usuário       |");
            System.out.println("| 0 - Voltar                |");
            System.out.print("Escolha uma opção: ");
            String opcao = scanner.nextLine().trim();

            switch (opcao) {
                case "1": consultarUsuariosAdmin(tokenAdmin, saida, entrada);          break;
                case "2": consultarUsuarioAdmin(scanner, tokenAdmin, saida, entrada);  break;
                case "3": atualizarUsuarioAdmin(scanner, tokenAdmin, saida, entrada);  break;
                case "4": deletarUsuarioAdmin(scanner, tokenAdmin, saida, entrada);    break;
                case "0": return;
                default:  System.err.println("Opção inválida!");
            }
        }
    }

    // ─── ADM LIST ───────────────────────────────────────────────────────────────

    private void consultarUsuariosAdmin(String tokenAdmin, PrintWriter saida, BufferedReader entrada) throws IOException {
        System.out.println("\n--- LISTAR TODOS USUÁRIOS (ADM) ---");

        JSONObject req = new JSONObject();
        req.put("op",          "consultarUsuariosAdmin");
        req.put("token_admin", tokenAdmin);

        enviarRequisicao(saida, req);
        JSONObject resp = receberResposta(entrada);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            JSONArray lista = resp.optJSONArray("lista_usuarios");
            if (lista == null || lista.isEmpty()) {
                System.out.println("[DADOS] Nenhum usuário cadastrado.");
            } else {
                System.out.println("[DADOS] " + lista.length() + " usuário(s):");
                for (int i = 0; i < lista.length(); i++) {
                    JSONObject u = lista.getJSONObject(i);
                    System.out.println("   - " + u.optString("usuario") + " (" + u.optString("nome") + ")");
                }
            }
        }
    }

    // ─── ADM READ ─────────────────────────────────────────────────────────────

    private void consultarUsuarioAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida, BufferedReader entrada) throws IOException {
        System.out.println("\n--- CONSULTAR USUÁRIO (ADM) ---");
        System.out.print("Usuário a consultar: ");
        String usuario = scanner.nextLine().trim();

        JSONObject req = new JSONObject();
        req.put("op",          "consultarUsuarioAdmin");
        req.put("token_admin", tokenAdmin);
        req.put("usuario",     usuario);

        enviarRequisicao(saida, req);
        JSONObject resp = receberResposta(entrada);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            System.out.println("[DADOS] Nome:    " + resp.optString("nome"));
            System.out.println("[DADOS] Usuário: " + resp.optString("usuario"));
        }
    }

    // ─── ADM UPDATE ─────────────────────────────────────────────────────────────

    private void atualizarUsuarioAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida, BufferedReader entrada) throws IOException {
        System.out.println("\n--- ATUALIZAR USUÁRIO (ADM) ---");
        System.out.print("Usuário a atualizar: ");
        String usuario = scanner.nextLine().trim();

        System.out.println("(Deixe em branco o campo que NÃO quer alterar)");
        System.out.print("Novo nome: ");
        String nome = scanner.nextLine().trim();

        System.out.print("Nova senha: ");
        String senha = scanner.nextLine().trim();

        JSONObject req = new JSONObject();
        req.put("op",          "atualizarUsuarioAdmin");
        req.put("token_admin", tokenAdmin);
        req.put("usuario",     usuario);
        // Campo em branco é enviado como nulo, conforme o protocolo.
        req.put("nome",  nome.isEmpty()  ? JSONObject.NULL : nome);
        req.put("senha", senha.isEmpty() ? JSONObject.NULL : senha);

        enviarRequisicao(saida, req);
        receberResposta(entrada);
    }

    // ─── ADM DELETE ─────────────────────────────────────────────────────────────

    private void deletarUsuarioAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida, BufferedReader entrada) throws IOException {
        System.out.println("\n--- DELETAR USUÁRIO (ADM) ---");
        System.out.print("Usuário a deletar: ");
        String usuario = scanner.nextLine().trim();

        System.out.print("Tem certeza? Esta ação é irreversível. (s/N): ");
        String confirmacao = scanner.nextLine().trim();
        if (!confirmacao.equalsIgnoreCase("s")) {
            System.out.println("[SISTEMA] Operação cancelada.");
            return;
        }

        JSONObject req = new JSONObject();
        req.put("op",          "deletarUsuarioAdmin");
        req.put("token_admin", tokenAdmin);
        req.put("usuario",     usuario);

        enviarRequisicao(saida, req);
        receberResposta(entrada);
    }

    // ─── AUXILIARES ───────────────────────────────────────────────────────────

    // Log local + envio pela rede — payload contém apenas o que o protocolo define
    private void enviarRequisicao(PrintWriter saida, JSONObject req) {
        System.out.println("\n[ ENVIADO → SERVIDOR ]");
        System.out.println(req.toString(4));
        System.out.println("--------------------------------------------------");
        saida.println(req.toString());
    }

    // Aguarda a resposta do servidor — uma única mensagem JSON conforme o protocolo
    private JSONObject receberResposta(BufferedReader entrada) throws IOException {
        String linha = entrada.readLine();

        if (linha == null) {
            System.err.println("[ERRO] O servidor fechou a conexão de forma inesperada.");
            return null;
        }

        try {
            JSONObject json = new JSONObject(linha);

            // Log local: imprime o que chegou pela rede
            System.out.println("\n[ RECEBIDO ← SERVIDOR ]");
            System.out.println(json.toString(4));
            System.out.println("==================================================");

            if (!json.has("resposta")) {
                System.err.println("[ALERTA PROTOCOLO] Resposta sem campo 'resposta'.");
                return null;
            }

            String codigo   = json.getString("resposta");
            String mensagem = json.optString("mensagem", "");

            if ("200".equals(codigo)) {
                if (!mensagem.isEmpty()) System.out.println("[SUCESSO] " + mensagem);
            } else if ("401".equals(codigo)) {
                System.err.println("[FALHA] " + mensagem);
            } else {
                System.err.println("[ALERTA PROTOCOLO] Código desconhecido: " + codigo);
            }

            return json;

        } catch (Exception e) {
            System.err.println("[ERRO] Resposta ininteligível do servidor: " + linha);
            return null;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("[SETUP] IP do servidor (ex: 10.20.40.51): ");
        String ip = scanner.nextLine().trim();

        System.out.print("[SETUP] Porta do servidor (ex: 21000): ");
        int porta = scanner.nextInt();
        scanner.nextLine();

        new Cliente(ip, porta).iniciar(scanner); // mesmo Scanner do setup, evita perda de buffer
    }
}