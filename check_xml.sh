#!/bin/bash
echo "Checking XML files for issues..."
echo "================================"

for file in $(find app/src/main/res -name "*.xml"); do
    # Check if file starts with <?xml
    if ! head -c 5 "$file" | grep -q "<?xml"; then
        echo "❌ Potential issue in: $file"
    else
        echo "✓ OK: $file"
    fi
done

echo "================================"
echo "Checking complete!"