package com.sistemasdistribuidos.cliente;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class Cliente {

    // Tempo de espera por uma resposta de comando (login, cadastro, consultas...).
    private static final long TIMEOUT_RESPOSTA = 5000;

    private final String ip;
    private final int    porta;
    private String tokenAtual = null; // null = deslogado

    // O servidor pode empurrar uma 'receberMensagem' a qualquer momento (S->C), então
    // a leitura do socket vive numa thread separada (escutarServidor). As respostas de
    // comando são entregues à thread do menu por este "correio": cada comando registra
    // um futuro aqui antes de enviar, e o ouvinte o completa quando a resposta chega.
    private final AtomicReference<CompletableFuture<JSONObject>> pendente = new AtomicReference<>();
    private volatile boolean conectado = true;

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

            // Ouvinte assíncrono: lê tudo que vem do servidor (respostas + mensagens push).
            Thread ouvinte = new Thread(() -> escutarServidor(entrada), "ouvinte-servidor");
            ouvinte.setDaemon(true);
            ouvinte.start();

            while (conectado) {
                exibirMenu();
                String opcao = scanner.nextLine().trim();

                switch (opcao) {
                    case "1": realizarCadastro(scanner, saida);    break;
                    case "2": realizarLogin(scanner, saida);       break;
                    case "3": realizarLogout(saida);               break;
                    case "4": consultarUsuario(saida);             break;
                    case "5": atualizarUsuario(scanner, saida);    break;
                    case "6": deletarUsuario(scanner, saida);      break;
                    case "7": enviarMensagem(scanner, saida);      break;
                    case "8": listarUsuariosLogados(saida);        break;
                    case "9": areaAdministrativa(scanner, saida);  break;
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
            System.out.println("| 1 - Cadastrar Usuário     |");
            System.out.println("| 2 - Login                 |");
        } else {
            System.out.println("| 3 - Logout                |");
            System.out.println("| 4 - Consultar Usuário     |");
            System.out.println("| 5 - Atualizar Usuário     |");
            System.out.println("| 6 - Deletar Conta         |");
            System.out.println("| 7 - Enviar Mensagem       |");
            System.out.println("| 8 - Listar Usuários Logados |");
            System.out.println("| Sessão: " + tokenAtual + " |");
        }
        System.out.println("| 9 - Área Administrativa   |");
        System.out.println("------------------------");
        System.out.print("Escolha uma opção: ");
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    private void realizarCadastro(Scanner scanner, PrintWriter saida) {
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

        enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);
    }

    // ─── LOGIN ────────────────────────────────────────────────────────────────

    private void realizarLogin(Scanner scanner, PrintWriter saida) {
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

        JSONObject resp = enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            tokenAtual = resp.getString("token");
            System.out.println("[SISTEMA] Token armazenado: " + tokenAtual);
            // Protocolo (EP-3): logo após o login o cliente pede, automaticamente,
            // a lista de usuários logados.
            listarUsuariosLogados(saida);
        }
    }

    // ─── LOGOUT ───────────────────────────────────────────────────────────────

    private void realizarLogout(PrintWriter saida) {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Você não está logado.");
            return;
        }

        System.out.println("\n--- LOGOUT ---");

        JSONObject req = new JSONObject();
        req.put("op",    "logout");
        req.put("token", tokenAtual);

        JSONObject resp = enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            tokenAtual = null;
        }
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    private void consultarUsuario(PrintWriter saida) {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Faça login para consultar seus dados.");
            return;
        }

        System.out.println("\n--- CONSULTAR USUÁRIO ---");

        JSONObject req = new JSONObject();
        req.put("op",    "consultarUsuario");
        req.put("token", tokenAtual);

        JSONObject resp = enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            System.out.println("[DADOS] Nome:    " + resp.optString("nome"));
            System.out.println("[DADOS] Usuário: " + resp.optString("usuario"));
        }
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    private void atualizarUsuario(Scanner scanner, PrintWriter saida) {
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

        enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    private void deletarUsuario(Scanner scanner, PrintWriter saida) {
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

        JSONObject resp = enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);

        // Só limpa a sessão local se a conta deletada era de fato a do próprio
        // usuário logado — assim, uma tentativa de fraude com token alheio não
        // afeta o estado do cliente.
        if (resp != null && "200".equals(resp.optString("resposta")) && token.equals(tokenAtual)) {
            tokenAtual = null;
        }
    }

    // ═══ ENTREGA 3 — MENSAGENS ═══════════════════════════════════════════════════

    // ─── ENVIAR MENSAGEM (direta ou broadcast) ──────────────────────────────────

    private void enviarMensagem(Scanner scanner, PrintWriter saida) {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Faça login para enviar mensagens.");
            return;
        }

        System.out.println("\n--- ENVIAR MENSAGEM ---");
        System.out.print("Destinatário (ou /todos para broadcast): ");
        String destinatario = scanner.nextLine().trim();

        System.out.print("Mensagem: ");
        String mensagem = scanner.nextLine();

        if (destinatario.isEmpty() || mensagem.isEmpty()) {
            System.err.println("[FALHA] Destinatário e mensagem devem estar preenchidos.");
            return;
        }

        JSONObject req = new JSONObject();
        req.put("op",           "enviarMensagem");
        req.put("token",        tokenAtual);
        req.put("destinatario", destinatario);
        req.put("mensagem",     mensagem);

        // O protocolo não define resposta de sucesso para 'enviarMensagem' (envio
        // "fire-and-forget"). Em caso de erro — p.ex. destinatário offline — o
        // servidor responde 401, que o ouvinte exibe assim que chega.
        enviarRequisicao(saida, req);
        System.out.println("[SISTEMA] Mensagem enviada para " + destinatario + ".");
    }

    // ─── LISTAR USUÁRIOS LOGADOS ────────────────────────────────────────────────

    private void listarUsuariosLogados(PrintWriter saida) {
        if (tokenAtual == null) {
            System.err.println("[AVISO] Faça login para listar os usuários logados.");
            return;
        }

        System.out.println("\n--- USUÁRIOS LOGADOS ---");

        JSONObject req = new JSONObject();
        req.put("op",    "listarUsuariosLogados");
        req.put("token", tokenAtual);

        JSONObject resp = enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);
        if (resp == null) return;

        // Protocolo (EP-3): no sucesso vem apenas 'lista_usuarios'; o erro vem como 401.
        JSONArray lista = resp.optJSONArray("lista_usuarios");
        if (lista == null) return; // erro já exibido pelo ouvinte

        if (lista.isEmpty()) {
            System.out.println("[LOGADOS] Nenhum usuário online.");
        } else {
            System.out.println("[LOGADOS] " + lista.length() + " usuário(s) online:");
            for (int i = 0; i < lista.length(); i++) {
                System.out.println("   • " + lista.getString(i));
            }
        }
    }

    // ═══ ENTREGA 2 — ÁREA ADMINISTRATIVA ════════════════════════════════════════
    // O administrador autentica fazendo login normal (admin/123456). O token de
    // sessão resultante — com role "adm" — é o que autoriza as operações de admin.

    private void areaAdministrativa(Scanner scanner, PrintWriter saida) {
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
        JSONObject resp = enviarEAguardar(saida, login, TIMEOUT_RESPOSTA);

        if (resp == null || !"200".equals(resp.optString("resposta"))) {
            return; // mensagem de falha já exibida pelo ouvinte
        }
        String tokenAdmin = resp.getString("token");
        if (!"adm".equals(tokenAdmin)) {
            System.err.println("[FALHA] Este usuário não possui privilégios de administrador.");
            return;
        }

        try {
            menuAdmin(scanner, tokenAdmin, saida);
        } finally {
            // Encerra a sessão administrativa ao sair da área.
            JSONObject logout = new JSONObject();
            logout.put("op",    "logout");
            logout.put("token", tokenAdmin);
            enviarEAguardar(saida, logout, TIMEOUT_RESPOSTA);
        }
    }

    private void menuAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida) {
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
                case "1": consultarUsuariosAdmin(tokenAdmin, saida);          break;
                case "2": consultarUsuarioAdmin(scanner, tokenAdmin, saida);  break;
                case "3": atualizarUsuarioAdmin(scanner, tokenAdmin, saida);  break;
                case "4": deletarUsuarioAdmin(scanner, tokenAdmin, saida);    break;
                case "0": return;
                default:  System.err.println("Opção inválida!");
            }
        }
    }

    // ─── ADM LIST ───────────────────────────────────────────────────────────────

    private void consultarUsuariosAdmin(String tokenAdmin, PrintWriter saida) {
        System.out.println("\n--- LISTAR TODOS USUÁRIOS (ADM) ---");

        JSONObject req = new JSONObject();
        req.put("op",          "consultarUsuariosAdmin");
        req.put("token_admin", tokenAdmin);

        JSONObject resp = enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);

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

    private void consultarUsuarioAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida) {
        System.out.println("\n--- CONSULTAR USUÁRIO (ADM) ---");
        System.out.print("Usuário a consultar: ");
        String usuario = scanner.nextLine().trim();

        JSONObject req = new JSONObject();
        req.put("op",          "consultarUsuarioAdmin");
        req.put("token_admin", tokenAdmin);
        req.put("usuario",     usuario);

        JSONObject resp = enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);

        if (resp != null && "200".equals(resp.optString("resposta"))) {
            System.out.println("[DADOS] Nome:    " + resp.optString("nome"));
            System.out.println("[DADOS] Usuário: " + resp.optString("usuario"));
        }
    }

    // ─── ADM UPDATE ─────────────────────────────────────────────────────────────

    private void atualizarUsuarioAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida) {
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

        enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);
    }

    // ─── ADM DELETE ─────────────────────────────────────────────────────────────

    private void deletarUsuarioAdmin(Scanner scanner, String tokenAdmin, PrintWriter saida) {
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

        enviarEAguardar(saida, req, TIMEOUT_RESPOSTA);
    }

    // ─── AUXILIARES ───────────────────────────────────────────────────────────

    // Ouvinte assíncrono: única thread que lê do socket. Diferencia o push de
    // mensagem (S->C, op="receberMensagem") — exibido na hora — das respostas de
    // comando, que são entregues a quem aguarda via o futuro 'pendente'.
    private void escutarServidor(BufferedReader entrada) {
        try {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                JSONObject json;
                try {
                    json = new JSONObject(linha);
                } catch (Exception e) {
                    System.err.println("[ERRO] Mensagem ininteligível do servidor: " + linha);
                    continue;
                }

                // Push de mensagem (S->C): exibido imediatamente, não é resposta de comando.
                if ("receberMensagem".equals(json.optString("op"))) {
                    logRecebido(json);
                    exibirMensagemRecebida(json);
                    continue;
                }

                // Demais payloads são respostas a um comando: registra, interpreta e
                // entrega ao solicitante (se houver algum aguardando).
                logRecebido(json);
                interpretar(json);
                CompletableFuture<JSONObject> f = pendente.getAndSet(null);
                if (f != null) {
                    f.complete(json);
                }
            }
        } catch (IOException e) {
            // conexão encerrada
        } finally {
            conectado = false;
            CompletableFuture<JSONObject> f = pendente.getAndSet(null);
            if (f != null) f.complete(null);
            System.err.println("\n[SISTEMA] Conexão com o servidor encerrada. Pressione ENTER para sair.");
        }
    }

    // Envia uma requisição e bloqueia até a resposta correspondente chegar (ou o
    // tempo esgotar). Usado pelas operações que esperam exatamente uma resposta.
    private JSONObject enviarEAguardar(PrintWriter saida, JSONObject req, long timeoutMs) {
        if (!conectado) {
            System.err.println("[ERRO] Sem conexão com o servidor.");
            return null;
        }
        CompletableFuture<JSONObject> futuro = new CompletableFuture<>();
        pendente.set(futuro);
        enviarRequisicao(saida, req);
        try {
            return futuro.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendente.compareAndSet(futuro, null);
            System.err.println("[ERRO] O servidor não respondeu a tempo.");
            return null;
        } catch (Exception e) {
            pendente.compareAndSet(futuro, null);
            return null;
        }
    }

    // Log local + envio pela rede — payload contém apenas o que o protocolo define
    private void enviarRequisicao(PrintWriter saida, JSONObject req) {
        System.out.println("\n[ ENVIADO → SERVIDOR ]");
        System.out.println(req.toString(4));
        System.out.println("--------------------------------------------------");
        saida.println(req.toString());
    }

    private void logRecebido(JSONObject json) {
        System.out.println("\n[ RECEBIDO ← SERVIDOR ]");
        System.out.println(json.toString(4));
        System.out.println("==================================================");
    }

    private void exibirMensagemRecebida(JSONObject json) {
        System.out.println("\n┌─── [ MENSAGEM RECEBIDA ] ───");
        System.out.println("│ De: " + json.optString("remetente"));
        System.out.println("│ " + json.optString("mensagem"));
        System.out.println("└─────────────────────────────");
    }

    // Imprime [SUCESSO]/[FALHA] das respostas de comando. A listagem de logados é o
    // único sucesso sem campo 'resposta' (conforme o protocolo) — nesse caso, silêncio.
    private void interpretar(JSONObject json) {
        if (!json.has("resposta")) {
            return;
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
