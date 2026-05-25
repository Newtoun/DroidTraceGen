import requests
import os
import glob

LOGS_DIR = "./analysis_data"
SAVE_DIR = "./result_tc"
OLLAMA_URL = "http://127.0.0.1:11435/api/generate"
MODEL = "llama3.1:70b"


def get_latest_log():
    files = glob.glob(os.path.join(LOGS_DIR, "log_*.txt"))
    if not files:
        raise FileNotFoundError(f"Nenhum log encontrado em '{LOGS_DIR}'")
    return max(files, key=os.path.getmtime)


def build_prompt(log_content):
    return f"""Você é um Engenheiro de QA Mobile. Com base nas ações de interface registradas abaixo, gere um caso de teste estruturado no formato TDD.

Formato de saída (siga estritamente esta estrutura):

ID DO CASO DE TESTE: TC_001
TÍTULO: <descrição curta do cenário>

PRÉ-CONDIÇÕES:
  - <o que deve ser verdadeiro antes do início do teste>

PASSOS:
  1. <ação>
     Resultado Esperado: <o que deve acontecer>
  2. <ação>
     Resultado Esperado: <o que deve acontecer>
  (continue para cada ação)

PÓS-CONDIÇÕES:
  - <estado final do sistema após o teste>

CRITÉRIOS DE APROVAÇÃO:
  - <condição que determina que o teste foi aprovado>

Regras:
- Cada ação registrada deve se tornar exatamente um passo numerado
- Os resultados esperados devem ser comportamentos observáveis na interface, não detalhes de implementação
- Seja conciso e preciso — esta saída será comparada com um caso de teste escrito manualmente
- Retorne apenas o bloco do caso de teste, sem explicações adicionais

Ações registradas:
{log_content}
"""


def save_result(result, source_log_path):
    base = os.path.splitext(os.path.basename(source_log_path))[0]
    out_path = os.path.join(SAVE_DIR, f"{base}_testcase.txt")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(result)
    return out_path


def main():
    log_path = get_latest_log()
    print(f"[*] Lendo log: {log_path}")

    with open(log_path, "r", encoding="utf-8") as f:
        log_content = f.read().strip()

    if not log_content:
        print("[!] Log vazio, abortando.")
        return

    print(f"[*] Enviando para o modelo '{MODEL}'...")

    response = requests.post(
        OLLAMA_URL,
        json={
            "model": MODEL,
            "prompt": build_prompt(log_content),
            "stream": False,
        },
        timeout=600,
    )
    response.raise_for_status()

    result = response.json()["response"]

    out_path = save_result(result, log_path)
    print(f"[+] Test case salvo em: {out_path}")
    print("\n--- RESULTADO ---\n")
    print(result)


if __name__ == "__main__":
    main()
