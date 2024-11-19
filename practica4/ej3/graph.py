import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns

# Load the CSV data
data = pd.read_csv('measurements.csv', header=None, names=['PlatformSize', 'Time'])

# Assign unique indices to entries within each PlatformSize for bar grouping
data['Entry'] = data.groupby('PlatformSize').cumcount()

# Calculate cumulative averages for the tendency lines
data['CumulativeAvg'] = data.groupby('PlatformSize')['Time'].expanding().mean().reset_index(level=0, drop=True)

# Pivot the data for bar plotting
pivot_data = data.pivot(index='Entry', columns='PlatformSize', values='Time')

# Plot the pivoted data as a bar chart
ax = pivot_data.plot(kind='bar', figsize=(14, 8), rot=0, color=sns.color_palette('husl', len(pivot_data.columns)))

# Separate legends for bars and lines
bar_handles, bar_labels = ax.get_legend_handles_labels()

# Plot cumulative average tendency lines
colors = sns.color_palette('husl', len(pivot_data.columns))  # Use the same color palette as bars
line_handles = []
line_labels = []
for idx, platform_size in enumerate(pivot_data.columns):
    # Extract cumulative averages for the current PlatformSize
    tendency_data = data[data['PlatformSize'] == platform_size]
    line, = plt.plot(
        tendency_data['Entry'],       # Entries (x-axis)
        tendency_data['CumulativeAvg'],  # Cumulative average (y-axis)
        marker='s',
        markersize=4,
        label=f'Tendency ({platform_size})',  # Add label for the line
        color=colors[idx],
        linewidth=2.5  # Thicker lines
    )
    # Add an outline to make the line more distinguishable
    plt.plot(
        tendency_data['Entry'], tendency_data['CumulativeAvg'],
        marker='s',
        color='black',
        linewidth=3,  # Thicker black outline
        alpha=0.3
    )
    line_handles.append(line)
    line_labels.append(f'Tendency ({platform_size})')

# Customize the plot
plt.xlabel('Entries')
plt.ylabel('Communication Time (ms)')
plt.title('Communication Time with Tendency')

# Add separate legends
bar_legend = plt.legend(bar_handles, bar_labels, title='Platform Size (Bars)', loc='center right')
plt.gca().add_artist(bar_legend)  # Add the bar legend first
plt.legend(line_handles, line_labels, title='Tendency Lines', loc='upper right')

plt.tight_layout()

# Show the plot
plt.show()

