package com.sistemasdistribuidos.cliente;

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

    public void iniciar() {
        Scanner scanner = new Scanner(System.in);
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
        req.put("token", tokenAtual);
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
        System.out.print("Tem certeza? Esta ação é irreversível. (s/N): ");
        String confirmacao = scanner.nextLine().trim();

        if (!confirmacao.equalsIgnoreCase("s")) {
            System.out.println("[SISTEMA] Operação cancelada.");
            return;
        }

        JSONObject req = new JSONObject();
        req.put("op",    "deletarUsuario");
        req.put("token", tokenAtual);

        enviarRequisicao(saida, req);
        JSONObject resp = receberResposta(entrada);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            tokenAtual = null;
        }
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

        System.out.print("[SETUP] IP do servidor (ex: 127.0.0.1 ou IP Radmin): ");
        String ip = scanner.nextLine().trim();

        System.out.print("[SETUP] Porta do servidor (ex: 8080): ");
        int porta = scanner.nextInt();
        scanner.nextLine();

        new Cliente(ip, porta).iniciar();
    }
}