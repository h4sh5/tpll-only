#!/usr/bin/env bash
#
# Boots a dedicated server with the mod on the classpath and drives /tpll, /tpllcalc and /where from its
# console, so both commands can be exercised without a client. The transcript is written to
# out/server-test-<version>.log.
#
#   tools/server-test.sh [minecraft-version]     (default: 26.3)
#
# The server is started through Loom's runServer task rather than a bare `java` command: only Loom
# knows the JVM properties and injection setup that make Fabric actually load mods in a dev
# runtime, and a hand-built classpath quietly gives you a plain vanilla server with no commands.
# Gradle forwards its own stdin to the server process, so a FIFO is all that is needed to type into
# the server console.
#
set -u

MC="${1:-26.3}"
cd "$(dirname "$0")/.."

# A previous run that didn't shut down cleanly still holds the server port, which looks like a
# startup failure of the mod rather than of the test harness.
pkill -f net.minecraft.server.Main 2>/dev/null
sleep 1

mkdir -p out

# runServer provides the mod itself; a copy in run/mods would be a second copy of it.
rm -f run/mods/tpll-only-*.jar

# A fresh world and config every run, otherwise a stale config hides the defaults.
rm -rf run/world run/config run/logs

# Keep off the default port for the same reason.
if [ -f run/server.properties ]; then
	sed -i "s/^server-port=.*/server-port=${PORT:-25577}/" run/server.properties
fi

LOG="out/server-test-${MC}.log"
CMDS="/tmp/tpll-cmds-${MC}"
: > "${CMDS}"

( tail -f "${CMDS}" | ./gradlew runServer -Pmc="${MC}" --console=plain > "${LOG}" 2>&1 ) &
RUNNER=$!

send() { printf '%s\n' "$1" >> "${CMDS}"; }

# Waits until the given string shows up in the log, or gives up.
await() {
	local want="$1" limit="${2:-300}" waited=0
	while [ "${waited}" -lt "${limit}" ]; do
		grep -qF "${want}" "${LOG}" 2>/dev/null && return 0
		sleep 2
		waited=$((waited + 2))
	done
	echo "TIMEOUT waiting for: ${want}" >&2
	return 1
}

echo "### ${MC}: booting"
if ! await 'Done ('; then
	tail -40 "${LOG}"
	pkill -f net.minecraft.server.Main 2>/dev/null
	kill "${RUNNER}" 2>/dev/null
	exit 1
fi

# /tpll needs a player, which the console isn't, so the target-less forms are checked on
# /tpllcalc (which is meant to work from the console) and /tpll is checked with a name and a
# selector.
send "tpll"
# Lucerne, the reference point for the default projection: BuildTheEarth's live server answers
# /tpllcalc here with X 3235623.703 Z -4704424.710, so this is where the two are compared.
send "tpllcalc 47.051939313728376, 8.306423216110339"
send "tpll 48.858370, 2.294481"
send "tpllcalc 48.858370, 2.294481"
send "tpllcalc 48.858370, 2.294481, 150"
send "tpllcalc N 48.858370 E 2.294481"
send "tpllcalc 48,858370 2,294481"
send "tpllcalc 48°51'30\"N 2°21'42\"E"
send "tpllcalc 0.00000000001 0.00000000001"
send "tpllcalc @a 35.6895 139.6917"
send "tpllcalc Steve 35.6895 139.6917"
send "tpll Steve 35.6895 139.6917"
send "tpll 91 0"
send "tpllcalc hello world"
send "tpllcalc"

# /where is the reverse of all of that. The console has no position of its own, so it gets the
# X/Z form; the player forms are checked with a name and a selector.
send "where"
send "where 3235497.43 -4704415.46"
send "where 3235497, -4704415, 70"
send "where 2847557.88 -5051340.18"
send "where -20000000 -20000000"
send "where 0 0"
send "where Steve"
send "where @a"
send "where hello world"
sleep 5
send "stop"

await 'Stopping server' 120 || true
sleep 3
pkill -f net.minecraft.server.Main 2>/dev/null
kill "${RUNNER}" 2>/dev/null

echo "### ${MC}: transcript (full log: ${LOG})"
grep -aE "\(tpll-only|Latitude / longitude|World position|Tplled|arrived|Usage:|Nothing to look up|permission|on Earth|Real world|In this world|outside the mapped|nobody standing here|Nobody named" "${LOG}" \
	| grep -av "<--\[HERE\]" | sed 's/^\[[^]]*\] \[[^]]*\] //'
