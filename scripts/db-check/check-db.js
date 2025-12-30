const fs = require('fs')
const path = require('path')
const dotenv = require('dotenv')
const { Client } = require('pg')

function loadEnvFile() {
  const root = path.resolve(__dirname, '..', '..')
  const envPath = path.join(root, '.env.postgres')
  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, 'utf8')
    return dotenv.parse(content)
  }
  // fallback to process.env
  return process.env
}

async function main() {
  const env = loadEnvFile()
  const host = env.POSTGRES_HOST || 'localhost'
  const port = Number(env.POSTGRES_PORT || env.PGPORT || 5433)
  const user = env.POSTGRES_USER || env.SPRING_DATASOURCE_USERNAME || env.PGUSER || 'postgres'
  const password = env.POSTGRES_PASSWORD || env.SPRING_DATASOURCE_PASSWORD || env.PGPASSWORD || ''
  const database = env.POSTGRES_DB || env.SPRING_DATASOURCE_DB || env.PGDATABASE || 'traveloptimizer'

  console.log('Connecting to Postgres', { host, port, user, database })
  const client = new Client({ host, port, user, password, database })
  try {
    await client.connect()
    const res = await client.query("SELECT column_name, data_type FROM information_schema.columns WHERE table_name='saved_offer';")
    console.log('Columns for saved_offer:')
    console.table(res.rows)
    const found = res.rows.find(r => r.column_name === 'value_score')
    if (found) {
      console.log('\nMigration check: value_score column EXISTS.')
      process.exit(0)
    } else {
      console.error('\nMigration check: value_score column NOT FOUND.')
      process.exit(2)
    }
  } catch (err) {
    console.error('Error connecting/querying Postgres:', err.message || err)
    process.exit(3)
  } finally {
    try { await client.end() } catch (e) {}
  }
}

main()
