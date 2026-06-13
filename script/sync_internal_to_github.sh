#!/usr/bin/env bash
# 把三个内网子仓库的所有分支镜像推送到 GitHub 上的对应 repo
#
# 用法：
#   ./script/sync_internal_to_github.sh           # 同步全部三个
#   ./script/sync_internal_to_github.sh xwBackend # 只同步指定模块
#
# 实现：使用临时 bare clone 做正确的镜像同步，不会污染工作仓库的 refs
#
# 前置条件：
#   - 你能 ssh 到 36.141.21.176:1111（公司内网/VPN）
#   - 你的 GitHub ssh key 有 GeezCo/xianwei_* 三个 repo 的写权限
#   - GitHub 上已经创建好对应的 repo（可以是 empty）

set -eo pipefail

MODULES=(xwBackend xwFrontend algorithm)

get_internal_url() {
  case "$1" in
    xwBackend)  echo "ssh://yinbanghu@36.141.21.176:1111/home/yinbanghu/repos/xianwei_server.git" ;;
    xwFrontend) echo "ssh://yinbanghu@36.141.21.176:1111/home/yinbanghu/repos/xianwei_web.git" ;;
    algorithm)  echo "ssh://yinbanghu@36.141.21.176:1111/home/yinbanghu/repos/xianwei_algo.git" ;;
  esac
}

get_github_url() {
  case "$1" in
    xwBackend)  echo "git@github.com:GeezCo/xianwei_server.git" ;;
    xwFrontend) echo "git@github.com:GeezCo/xianwei_web.git" ;;
    algorithm)  echo "git@github.com:GeezCo/xianwei_algo.git" ;;
  esac
}

sync_one() {
  local module="$1"
  local internal_url
  local github_url
  internal_url="$(get_internal_url "$module")"
  github_url="$(get_github_url "$module")"

  if [ -z "$internal_url" ]; then
    echo "未知模块：$module"
    return 1
  fi

  echo "=== [$module] 同步开始 ==="

  # 临时 bare clone（不污染本地开发仓库）
  local tmpdir
  tmpdir="$(mktemp -d -t "sync_${module}.XXXXXX")"
  trap 'rm -rf "$tmpdir"' RETURN

  echo "[$module] 从内网 mirror clone 到临时目录..."
  git clone --mirror "$internal_url" "$tmpdir/repo.git"

  cd "$tmpdir/repo.git"

  echo "[$module] 推送到 GitHub --mirror..."
  git push --mirror "$github_url"

  cd - >/dev/null
  rm -rf "$tmpdir"
  trap - RETURN

  echo "=== [$module] 同步完成 ==="
}

main() {
  local targets
  if [ $# -eq 0 ]; then
    targets=("${MODULES[@]}")
  else
    targets=("$@")
  fi

  for m in "${targets[@]}"; do
    local ok=0
    for valid in "${MODULES[@]}"; do
      if [ "$m" = "$valid" ]; then ok=1; break; fi
    done
    if [ $ok -eq 0 ]; then
      echo "未知模块：$m（可选：${MODULES[*]}）"
      exit 1
    fi
    sync_one "$m"
  done

  echo "全部同步完成。"
}

main "$@"
