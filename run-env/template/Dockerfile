FROM node:lts-bookworm-slim

# 汎用ランタイム（Python + Node）。個別スキルの追加依存は後段で差し込む。
RUN apt-get update \
  && apt-get install -y python3 python3-pip python3-venv sudo \
  && rm -rf /var/lib/apt/lists/*

# PEP 668 対応のため venv を作成し、以降の pip を仮想環境へ誘導する。
RUN python3 -m venv /opt/venv
ENV VIRTUAL_ENV=/opt/venv
ENV PATH="${VIRTUAL_ENV}/bin:${PATH}"

WORKDIR /workspace
COPY build/skills/ /workspace/skills/

# @skill-commands
