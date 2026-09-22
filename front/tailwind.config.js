/** @type {import('tailwindcss').Config} */
export default {
  // ⚠️ 重要：必须配置 content，否则 Tailwind 不会生成任何样式！
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          blue: '#4687ff',
          darkBlue: '#3279ff',
          text: '#566a7f',
          border: '#d9dee3',
          bg: '#f5f5f9'
        }
      }
    }
  },
  plugins: [],
}