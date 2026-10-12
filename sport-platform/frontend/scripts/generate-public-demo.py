"""Generate a compact six-heat fixture from the canonical set-M athletes."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[3]
source = json.loads((root / 'docs/scripts/demo/out/set-M.json').read_text())
athletes = {a['id']: a for a in source['athletes']}
sessions = [dict(id=f'session-{i+1}', name=name, orderNo=i+1, startsAt=f'2026-10-15T{hour}:00:00+03:00', breakBetweenEventsSec=60) for i, (name, hour) in enumerate([('Сессия 1 · утро', '10'), ('Сессия 2 · день', '14')])]
plans = [('M-50-FREE', 50, 'M', 2, 0), ('F-50-FREE', 50, 'F', 2, 0), ('M-100-FREE', 100, 'M', 1, 1), ('F-100-FREE', 100, 'F', 1, 1)]
disciplines, start_sessions, rows_by_discipline, schedule = [], [], {}, []
for s in sessions: start_sessions.append(dict(id=s['id'], name=s['name'], disciplines=[]))
sequence = [0, 0]
for number, (code, distance, gender, count, session_index) in enumerate(plans, 1):
    name = f'{distance} м вольный стиль, ' + ('юноши' if gender == 'M' else 'девушки')
    discipline = dict(id=code, number=number, name=name, distanceM=distance, stroke='FREE', gender=gender, sessionId=sessions[session_index]['id'], orderInSession=1 if number % 2 else 2, unitsPublished=True)
    disciplines.append(discipline)
    entries = [e for e in source['entries'] if e['disciplineCode'] == code][:count*8-2]
    entries.sort(key=lambda e: e['seedTimeMs'] or 999999, reverse=True)
    heats, rows = [], []
    for heat_index in range(count):
        sequence[session_index] += 1
        seconds = (sequence[session_index] - 1) * (90 if distance == 50 else 150)
        scheduled_at = f'2026-10-15T{10 if session_index == 0 else 14:02d}:{seconds//60:02d}:{seconds%60:02d}+03:00'
        heat = dict(id=f'{code}-{heat_index+1}', number=heat_index+1, ageGroupCode=None, scheduledAt=scheduled_at, lanes=[])
        for index, entry in enumerate(entries[heat_index*8:(heat_index+1)*8]):
            athlete = athletes[entry['athleteId']]
            identity = {k: athlete[k] for k in ['lastName', 'firstName', 'birthYear', 'club']}
            lane = dict(lane=[4, 5, 3, 6, 2, 7, 1, 8][index], registrationId=f'reg-{code}-{athlete["id"]}', athlete=identity, seedTimeMs=None if index == 0 else entry['seedTimeMs'])
            if number == 1 and heat_index == 0 and index == 7: lane['withdrawn'] = True
            heat['lanes'].append(lane)
            status = ['DNS', 'DNF', 'DSQ'][index-5] if heat_index == 0 and index >= 5 else 'VALID'
            result_ms = (entry['seedTimeMs'] or 55000) - 250
            if number == 1 and len(rows) == 1: result_ms = rows[0]['timeMs']
            centiseconds = result_ms // 10
            display = f'{centiseconds//6000:02d}:{(centiseconds//100)%60:02d}.{centiseconds%100:02d}' if status == 'VALID' else None
            group_from = (athlete['birthYear']//2)*2
            rows.append(dict(**identity, athleteKey=str(athlete['id']), status=status, timeMs=result_ms if status == 'VALID' else None, display=display, place=None, placeInAgeGroup=None, ageGroupCode=f'{gender}-{group_from}-{group_from+1}'))
        occupied = {lane['lane'] for lane in heat['lanes']}
        heat['lanes'].extend(dict(lane=lane, empty=True) for lane in range(1, 9) if lane not in occupied)
        heats.append(heat)
        schedule.append(dict(sessionName=sessions[session_index]['name'], disciplineName=name, heatNumber=heat_index+1, sequenceInSession=sequence[session_index], scheduledAt=scheduled_at, status='PUBLISHED'))
    rows.sort(key=lambda row: (row['status'] != 'VALID', row['timeMs'] or 999999))
    for i, row in enumerate(rows):
        if row['status'] == 'VALID': row['place'] = rows[i-1]['place'] if i and rows[i-1]['timeMs'] == row['timeMs'] else i+1
    groups = []
    for code in sorted(set(row['ageGroupCode'] for row in rows)):
        group_rows = [row for row in rows if row['ageGroupCode'] == code]
        for i, row in enumerate(group_rows):
            if row['status'] == 'VALID': row['placeInAgeGroup'] = group_rows[i-1]['placeInAgeGroup'] if i and group_rows[i-1]['timeMs'] == row['timeMs'] else i+1
        groups.append(dict(code=code, name=('Юноши ' if gender == 'M' else 'Девушки ') + code[2:].replace('-', '–'), rows=[{k: (row['placeInAgeGroup'] if k == 'place' else row[k]) for k in ['place', 'lastName', 'firstName', 'birthYear', 'club', 'display', 'timeMs', 'status']} for row in group_rows]))
    rows_by_discipline[discipline['id']] = dict(id=discipline['id'], number=number, name=name, overall=[{k: row[k] for k in ['place', 'lastName', 'firstName', 'birthYear', 'club', 'display', 'timeMs', 'status']} for row in rows], byAgeGroup=groups)
    start_sessions[session_index]['disciplines'].append(dict(id=discipline['id'], number=number, name=name, heats=heats))
event = dict(id='demo-event', sportId='swimming', status='REGISTRATION_OPEN', registrationOpenAt='2026-10-10T00:00:00+03:00', registrationCloseAt='2026-10-14T23:59:00+03:00', title='Осенний кубок Flounder', description='Демонстрационное соревнование для юношей и девушек 2010–2015 годов рождения.', publicSlug='demo', eventStartAt=sessions[0]['startsAt'], eventEndAt='2026-10-15T17:00:00+03:00', registrationOpen=True, venue=dict(name='Бассейн «Дельфин»', city='Самара', lanes=8, courseLength=25), sessions=sessions, disciplines=disciplines)
form = dict(requiresSeedTime=True, disciplines=[dict(id=d['id'], name=d['name'], distanceM=d['distanceM'], stroke=d['stroke'], gender=d['gender'], entryFee=0, ageGroups=[dict(code=f'{d["gender"]}-{year}-{year+1}', name=f'{year}–{year+1}', gender=d['gender'], birthYearFrom=year, birthYearTo=year+1) for year in [2010, 2012, 2014]]) for d in disciplines])
values = [('demoEvent', 'PublicEvent', event), ('demoStartList', 'StartList', dict(sessions=start_sessions)), ('demoResults', 'Results', dict(disciplines=list(rows_by_discipline.values()))), ('demoSchedule', 'ScheduleItem[]', schedule), ('demoForm', 'RegistrationForm', form)]
output = "// Generated by scripts/generate-public-demo.py from set-M.json.\nimport type { PublicEvent, StartList, Results, ScheduleItem, RegistrationForm } from '../features/public/public.types'\n\n"
for variable, typename, value in values: output += f'export const {variable}: {typename} = ' + json.dumps(value, ensure_ascii=False, indent=2) + '\n\n'
(root / 'sport-platform/frontend/src/mocks/demo-data.ts').write_text(output)
