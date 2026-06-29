package com.sistemasdistribuidos.cliente;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

// Motor de rede do cliente: cuida do socket, da thread leitora e do envio de
// requisições. Não conhece nenhuma tela — fala com a UI por meio de um Ouvinte.
public class ClienteRede {

    public interface Ouvinte {
        void aoReceberMensagem(String remetente, String mensagem); // push S->C
        void aoLog(String linha);                                  // tráfego do protocolo
        void aoDesconectar();                                      // conexão encerrada
    }

    private final Socket socket;
    private final PrintWriter saida;
    private final BufferedReader entrada;
    private final Ouvinte ouvinte;

    // Cada comando registra um futuro aqui antes de enviar; a thread leitora o
    // completa quando a resposta correspondente chega. Os pushes de mensagem
    // (receberMensagem) não passam por aqui — vão direto ao Ouvinte.
    private final AtomicReference<CompletableFuture<JSONObject>> pendente = new AtomicReference<>();
    private volatile boolean conectado = true;

    public ClienteRede(String ip, int porta, Ouvinte ouvinte) throws IOException {
        this.ouvinte = ouvinte;
        this.socket  = new Socket(ip, porta);
        this.saida   = new PrintWriter(socket.getOutputStream(), true);
        this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        Thread leitor = new Thread(this::escutar, "cliente-ouvinte");
        leitor.setDaemon(true);
        leitor.start();
    }

    public boolean isConectado() {
        return conectado;
    }

    public void fechar() {
        conectado = false;
        try { socket.close(); } catch (IOException ignorado) { }
    }

    private void escutar() {
        try {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                JSONObject json;
                try {
                    json = new JSONObject(linha);
                } catch (Exception e) {
                    ouvinte.aoLog("[ERRO] Mensagem ininteligível do servidor: " + linha);
                    continue;
                }

                ouvinte.aoLog("\n[ RECEBIDO ← SERVIDOR ]\n"
                        + json.toString(4)
                        + "\n==================================================");

                // Push de mensagem (S->C): entregue na hora, não é resposta de comando.
                if ("receberMensagem".equals(json.optString("op"))) {
                    ouvinte.aoReceberMensagem(json.optString("remetente"), json.optString("mensagem"));
                    continue;
                }

                // Resposta a um comando: entrega a quem estiver aguardando.
                CompletableFuture<JSONObject> f = pendente.getAndSet(null);
                if (f != null) f.complete(json);
            }
        } catch (IOException e) {
            // conexão encerrada
        } finally {
            conectado = false;
            CompletableFuture<JSONObject> f = pendente.getAndSet(null);
            if (f != null) f.complete(null);
            ouvinte.aoDesconectar();
        }
    }

    // Envio "fire-and-forget" (ex.: enviarMensagem, cujo sucesso não tem resposta).
    public void enviar(JSONObject req) {
        ouvinte.aoLog("\n[ ENVIADO → SERVIDOR ]\n"
                + req.toString(4)
                + "\n--------------------------------------------------");
        saida.println(req.toString());
    }

    // Envia e bloqueia até a resposta chegar (ou estourar o tempo). NÃO deve ser
    // chamado na thread de UI — use uma thread de trabalho.
    public JSONObject enviarEAguardar(JSONObject req, long timeoutMs) {
        if (!conectado) return null;
        CompletableFuture<JSONObject> futuro = new CompletableFuture<>();
        pendente.set(futuro);
        enviar(req);
        try {
            return futuro.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendente.compareAndSet(futuro, null);
            return null;
        } catch (Exception e) {
            pendente.compareAndSet(futuro, null);
            return null;
        }
    }
}
