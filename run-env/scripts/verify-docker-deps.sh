#!/usr/bin/env bash
set -euo pipefail

skills_root="${1:-build/skills}"

if [[ ! -d "$skills_root" ]]; then
  echo "SKILL ルートが見つかりません: $skills_root" >&2
  exit 1
fi

deps_files=$(find "$skills_root" -type f -path "*/.skill-runtime/skill-deps.yaml")
if [[ -z "$deps_files" ]]; then
  echo "skill-deps.yaml が見つかりません: $skills_root" >&2
  exit 1
fi

read_list() {
  local deps_file="$1"
  local key="$2"
  ruby -ryaml -e '
    data = YAML.load_file(ARGV[0]) || {}
    commands = Array(data["commands"]).compact
    key = ARGV[1]
    results = []

    def normalize_tokens(segment)
      tokens = segment.split(/\s+/).map do |token|
        token.gsub(/\A["\x27]|["\x27]\z/, "")
      end
      tokens.reject do |token|
        token.empty? || token.start_with?("-") || token.end_with?(".txt", ".in", ".lock", ".whl", ".tar.gz", ".zip")
      end
    end

    commands.each do |cmd|
      case key
      when "npm"
        if cmd =~ /npm\s+install(?:\s+-g)?\s+(.+)/i
          segment = $1.split(/&&|;/).first
          results.concat(normalize_tokens(segment))
        end
      when "pip"
        if cmd =~ /(?:pip3?|python\s+-m\s+pip)\s+install\s+(.+)/i
          segment = $1.split(/&&|;/).first
          results.concat(normalize_tokens(segment))
        end
      when "apt"
        if cmd =~ /apt(?:-get)?\s+install\s+(.+)/i
          segment = $1.split(/&&|;/).first
          results.concat(normalize_tokens(segment))
        end
      end
    end

    uniq = []
    results.each { |item| uniq << item unless uniq.include?(item) }
    puts uniq.join(" ")
  ' "$deps_file" "$key"
}

for deps_file in $deps_files; do
  runtime_dir=$(dirname "$deps_file")
  skill_dir=$(dirname "$runtime_dir")
  dockerfile="$runtime_dir/Dockerfile"

  if [[ ! -f "$dockerfile" ]]; then
    echo "Dockerfile が見つかりません: $dockerfile" >&2
    exit 1
  fi

  skill_rel="${skill_dir#${skills_root}/}"
  tag_suffix=$(echo "$skill_rel" | tr '/[:upper:]' '-[:lower:]')
  tag="skill-deps-${tag_suffix}"
  echo "Dockerfile をビルドします: $dockerfile"
  docker build -t "$tag" -f "$dockerfile" .

  npm_packages=$(read_list "$deps_file" "npm")
  pip_packages=$(read_list "$deps_file" "pip")
  apt_packages=$(read_list "$deps_file" "apt")

  if [[ -n "$npm_packages" ]]; then
    for pkg in $npm_packages; do
      echo "npm 依存を検証します: $pkg"
      docker run --rm "$tag" sh -lc "NODE_PATH=\$(npm root -g) node -e \"require.resolve('${pkg}')\""
    done
  fi

  if [[ -n "$pip_packages" ]]; then
    for pkg in $pip_packages; do
      echo "pip 依存を検証します: $pkg"
      pkg_name="${pkg%%[*}"
      pkg_name="${pkg_name%%[<>=~]*}"
      docker run --rm "$tag" sh -lc "/opt/venv/bin/python -m pip show ${pkg_name} >/dev/null"
    done
  fi

  if [[ -n "$apt_packages" ]]; then
    for pkg in $apt_packages; do
      echo "apt 依存を検証します: $pkg"
      docker run --rm "$tag" sh -lc "dpkg -s ${pkg} >/dev/null"
    done
  fi
done
